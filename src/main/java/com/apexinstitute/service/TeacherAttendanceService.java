package com.apexinstitute.service;

import com.apexinstitute.dto.AttendanceRosterDto;
import com.apexinstitute.dto.AttendanceRosterDto.StudentRow;
import com.apexinstitute.dto.SaveAttendanceRequest;
import com.apexinstitute.dto.TeacherClassesDto;
import com.apexinstitute.dto.TeacherClassesDto.ClassItem;
import com.apexinstitute.dto.TeacherClassesDto.TeacherSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TeacherAttendanceService {

    private final JdbcTemplate jdbc;

    public TeacherAttendanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- Teacher identity helper ---------- */
    private TeacherSummary currentTeacher() {
        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        return jdbc.queryForObject("""
                SELECT t.id, t.teacher_id, u.full_name
                FROM teachers t
                JOIN users u ON u.id = t.user_id
                WHERE t.user_id = ?
                """,
                (rs, i) -> new TeacherSummary(
                        rs.getLong("id"),
                        rs.getString("teacher_id"),
                        rs.getString("full_name")),
                userId);
    }

    /* ---------- GET /api/teacher/classes ---------- */
    public TeacherClassesDto classes() {
        TeacherSummary teacher = currentTeacher();
        if (teacher == null) throw new RuntimeException("Teacher record not found");

        List<ClassItem> list = jdbc.query("""
                SELECT DISTINCT
                       c.id, c.name, c.grade, s.name AS subject,
                       t.day_of_week, t.start_time, t.end_time, r.name AS room,
                       (SELECT COUNT(*) FROM enrollments e
                        WHERE e.class_id = c.id AND e.is_active = 1) AS students,
                       (SELECT CASE WHEN COUNT(*) = 0 THEN 0.0
                                    ELSE SUM(CASE WHEN a.status='Present' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)
                               END
                        FROM attendance a
                        WHERE a.class_id = c.id) AS attendance_rate
                FROM timetable t
                JOIN classes  c ON c.id = t.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN rooms    r ON r.id = t.room_id
                WHERE t.teacher_id = ?
                ORDER BY s.name, c.grade
                """,
                (rs, i) -> new ClassItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject"),
                        rs.getString("grade"),
                        rs.getString("day_of_week"),
                        trim(rs.getString("start_time")) + " – " + trim(rs.getString("end_time")),
                        rs.getString("room"),
                        rs.getInt("students"),
                        Math.round(rs.getDouble("attendance_rate") * 10.0) / 10.0),
                teacher.id());

        return new TeacherClassesDto(teacher, list);
    }

    /* ---------- GET /api/teacher/attendance/roster ---------- */
    public AttendanceRosterDto roster(Long classId, String dateStr) {

        TeacherSummary teacher = currentTeacher();

        // Class must belong to this teacher
        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM timetable
                WHERE class_id = ? AND teacher_id = ?
                """, Integer.class, classId, teacher.id());
        if (owns == null || owns == 0)
            throw new RuntimeException("Class not taught by you");

        // Class info
        ClassMeta meta = jdbc.queryForObject("""
                SELECT c.id, c.name, c.grade, s.name AS subject
                FROM classes c
                JOIN subjects s ON s.id = c.subject_id
                WHERE c.id = ?
                """,
                (rs, i) -> new ClassMeta(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("grade"),
                        rs.getString("subject")),
                classId);

        LocalDate date = LocalDate.parse(dateStr);

        // Roster = enrolled students, with existing attendance if any
        List<StudentRow> roster = jdbc.query("""
                SELECT s.id AS student_id,
                       s.student_id AS student_code,
                       s.rfid_tag,
                       u.full_name,
                       a.status AS current_status
                FROM enrollments en
                JOIN students s ON s.id = en.student_id
                JOIN users    u ON u.id = s.user_id
                LEFT JOIN attendance a
                       ON a.student_id = s.id
                      AND a.class_id = ?
                      AND a.[date] = ?
                WHERE en.class_id = ? AND en.is_active = 1
                ORDER BY u.full_name
                """,
                (rs, i) -> new StudentRow(
                        rs.getLong("student_id"),
                        rs.getString("student_code"),
                        rs.getString("full_name"),
                        rs.getString("rfid_tag"),
                        rs.getString("current_status") == null ? "Present" : rs.getString("current_status")),
                classId, Date.valueOf(date), classId);

        // Was attendance for this class + date already saved? Read from any row.
        MarkInfo markInfo = jdbc.query("""
                SELECT TOP 1
                       u.full_name AS marker,
                       a.created_at
                FROM attendance a
                LEFT JOIN users u ON u.id = a.marked_by
                WHERE a.class_id = ? AND a.[date] = ?
                ORDER BY a.created_at DESC
                """,
                rs -> {
                    if (!rs.next()) return null;
                    Timestamp ts = rs.getTimestamp("created_at");
                    return new MarkInfo(
                            rs.getString("marker"),
                            ts == null ? null
                                    : ts.toLocalDateTime().format(
                                    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")));
                },
                classId, Date.valueOf(date));

        boolean editing = markInfo != null;

        return new AttendanceRosterDto(
                meta.id, meta.name, meta.subject, meta.grade,
                date.toString(),
                editing,
                editing ? markInfo.marker() : null,
                editing ? markInfo.markedAt() : null,
                roster);
    }

    /* ---------- POST /api/teacher/attendance/save ---------- */
    @Transactional
    public int save(SaveAttendanceRequest req) {

        TeacherSummary teacher = currentTeacher();

        // Class must belong to this teacher
        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM timetable
                WHERE class_id = ? AND teacher_id = ?
                """, Integer.class, req.getClassId(), teacher.id());
        if (owns == null || owns == 0)
            throw new RuntimeException("Class not taught by you");

        LocalDate date = LocalDate.parse(req.getDate());
        Long teacherUserId = jdbc.queryForObject(
                "SELECT user_id FROM teachers WHERE id = ?", Long.class, teacher.id());

        int written = 0;
        for (SaveAttendanceRequest.Entry e : req.getEntries()) {

            String newStatus = e.getStatus();
            if (!newStatus.equals("Present") && !newStatus.equals("Late") && !newStatus.equals("Absent"))
                throw new RuntimeException("Invalid status: " + newStatus);

            // Verify the student is enrolled in the class
            Integer enrolled = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM enrollments
                    WHERE class_id = ? AND student_id = ? AND is_active = 1
                    """, Integer.class, req.getClassId(), e.getStudentId());
            if (enrolled == null || enrolled == 0)
                throw new RuntimeException("Student " + e.getStudentId() + " not enrolled in this class");

            // Existing row for this (student, class, date)?
            Long existingId = jdbc.query("""
                    SELECT id FROM attendance
                    WHERE student_id = ? AND class_id = ? AND [date] = ?
                    """,
                    rs -> rs.next() ? rs.getLong("id") : null,
                    e.getStudentId(), req.getClassId(), Date.valueOf(date));

            if (existingId == null) {
                // Insert fresh
                jdbc.update("""
                        INSERT INTO attendance
                            (student_id, class_id, [date], status, source, marked_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'MANUAL', ?, SYSDATETIME(), SYSDATETIME())
                        """,
                        e.getStudentId(), req.getClassId(), Date.valueOf(date),
                        newStatus, teacherUserId);
                written++;
            } else {
                // Read current status; only log + update if it changed
                String oldStatus = jdbc.queryForObject(
                        "SELECT status FROM attendance WHERE id = ?",
                        String.class, existingId);

                if (!oldStatus.equals(newStatus)) {
                    jdbc.update("""
                            UPDATE attendance
                            SET status = ?, source = 'MANUAL', marked_by = ?, updated_at = SYSDATETIME()
                            WHERE id = ?
                            """, newStatus, teacherUserId, existingId);

                    jdbc.update("""
                            INSERT INTO attendance_logs
                                (attendance_id, changed_by, old_status, new_status, changed_at)
                            VALUES (?, ?, ?, ?, SYSDATETIME())
                            """, existingId, teacherUserId, oldStatus, newStatus);
                    written++;
                }
            }
        }
        return written;
    }

    /* ---------- internal helpers ---------- */
    private static String trim(String t) {
        if (t == null) return "";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private record ClassMeta(Long id, String name, String grade, String subject) {}
    private record MarkInfo(String marker, String markedAt) {}
}