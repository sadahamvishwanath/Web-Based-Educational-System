package com.apexinstitute.service;

import com.apexinstitute.dto.StudentClassesDto;
import com.apexinstitute.dto.StudentClassesDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClassesService {

    private final JdbcTemplate jdbc;

    public ClassesService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public StudentClassesDto forCurrentStudent() {

        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        // ---------- Student ----------
        StudentSummary student = jdbc.queryForObject("""
                SELECT s.id, s.student_id, s.grade, u.full_name
                FROM students s
                JOIN users u ON u.id = s.user_id
                WHERE s.user_id = ?
                """,
                (rs, i) -> new StudentSummary(
                        rs.getLong("id"),
                        rs.getString("student_id"),
                        rs.getString("full_name"),
                        rs.getString("grade")),
                userId);

        if (student == null) throw new RuntimeException("Student record not found");
        Long studentPk = student.id();

        // ---------- Enrolled classes ----------
        List<EnrolledClass> enrolled = jdbc.query("""
                SELECT c.id, c.name, c.grade,
                       s.name AS subject,
                       u.full_name AS teacher,
                       r.name AS room,
                       t.day_of_week, t.start_time, t.end_time,
                       en.enrolled_at
                FROM enrollments en
                JOIN classes  c ON c.id = en.class_id
                JOIN subjects s ON s.id = c.subject_id
                LEFT JOIN timetable t ON t.id = (
                    SELECT TOP 1 id FROM timetable
                    WHERE class_id = c.id
                    ORDER BY start_time
                )
                LEFT JOIN teachers te ON te.id = t.teacher_id
                LEFT JOIN users    u  ON u.id = te.user_id
                LEFT JOIN rooms    r  ON r.id = t.room_id
                WHERE en.student_id = ? AND en.is_active = 1
                ORDER BY s.name, c.grade
                """,
                (rs, i) -> {
                    Date d = rs.getDate("enrolled_at");
                    String enrolledOn = d == null ? "—"
                            : d.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                    return new EnrolledClass(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("subject"),
                            rs.getString("grade"),
                            orDash(rs.getString("teacher")),
                            orDash(rs.getString("day_of_week")),
                            formatSlot(rs.getString("start_time"), rs.getString("end_time")),
                            orDash(rs.getString("room")),
                            enrolledOn);
                },
                studentPk);

        // ---------- Available classes ----------
        List<AvailableClass> available = jdbc.query("""
                SELECT c.id, c.name, c.grade,
                       s.name AS subject,
                       u.full_name AS teacher,
                       r.name AS room,
                       t.day_of_week, t.start_time, t.end_time
                FROM classes c
                JOIN subjects s ON s.id = c.subject_id
                LEFT JOIN timetable t ON t.id = (
                    SELECT TOP 1 id FROM timetable
                    WHERE class_id = c.id
                    ORDER BY start_time
                )
                LEFT JOIN teachers te ON te.id = t.teacher_id
                LEFT JOIN users    u  ON u.id = te.user_id
                LEFT JOIN rooms    r  ON r.id = t.room_id
                WHERE c.is_active = 1
                  AND NOT EXISTS (
                      SELECT 1 FROM enrollments en
                      WHERE en.student_id = ? AND en.class_id = c.id AND en.is_active = 1
                  )
                ORDER BY s.name, c.grade
                """,
                (rs, i) -> new AvailableClass(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject"),
                        rs.getString("grade"),
                        orDash(rs.getString("teacher")),
                        orDash(rs.getString("day_of_week")),
                        formatSlot(rs.getString("start_time"), rs.getString("end_time")),
                        orDash(rs.getString("room"))),
                studentPk);

        // ---------- Stats ----------
        int subjectCount = (int) enrolled.stream().map(EnrolledClass::subject).distinct().count();
        int teacherCount = (int) enrolled.stream()
                .map(EnrolledClass::teacher)
                .filter(t -> !"—".equals(t))
                .distinct().count();

        Stats stats = new Stats(
                enrolled.size(),
                subjectCount,
                teacherCount,
                available.size());

        return new StudentClassesDto(student, stats, enrolled, available);
    }

    // ---------- Enroll ----------
    @Transactional
    public void enroll(Long classId) {

        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        Long studentPk = jdbc.queryForObject(
                "SELECT id FROM students WHERE user_id = ?",
                Long.class, userId);

        if (studentPk == null) throw new RuntimeException("Student record not found");

        // Class must exist and be active
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM classes WHERE id = ? AND is_active = 1",
                Integer.class, classId);
        if (active == null || active == 0)
            throw new RuntimeException("Class not found or inactive");

        // Already enrolled (active)?
        Integer already = jdbc.queryForObject("""
                SELECT COUNT(*) FROM enrollments
                WHERE student_id = ? AND class_id = ? AND is_active = 1
                """, Integer.class, studentPk, classId);
        if (already != null && already > 0)
            throw new RuntimeException("You are already enrolled in this class");

        // Try to reactivate an old row first, else insert
        Integer reactivated = jdbc.update("""
                UPDATE enrollments
                SET is_active = 1, enrolled_at = CAST(SYSDATETIME() AS DATE)
                WHERE student_id = ? AND class_id = ? AND is_active = 0
                """, studentPk, classId);

        if (reactivated == 0) {
            jdbc.update("""
                    INSERT INTO enrollments (student_id, class_id, enrolled_at, is_active)
                    VALUES (?, ?, CAST(SYSDATETIME() AS DATE), 1)
                    """, studentPk, classId);
        }
    }

    private static String orDash(String s) { return s == null ? "—" : s; }

    private static String formatSlot(String start, String end) {
        if (start == null || end == null) return "Not scheduled";
        return trim(start) + " – " + trim(end);
    }

    private static String trim(String t) {
        if (t == null) return "";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }
}