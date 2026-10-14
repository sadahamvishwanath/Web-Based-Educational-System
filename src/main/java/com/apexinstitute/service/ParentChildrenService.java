package com.apexinstitute.service;

import com.apexinstitute.dto.ParentChildDetailDto;
import com.apexinstitute.dto.ParentChildDetailDto.*;
import com.apexinstitute.dto.ParentChildrenDto;
import com.apexinstitute.dto.ParentChildrenDto.ChildFull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ParentChildrenService {

    private final JdbcTemplate jdbc;
    private final ParentContext ctx;

    private static final List<String> DAY_ORDER = List.of(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private static final List<Slot> DEFAULT_SLOTS = List.of(
            new Slot("08:00", "10:00"), new Slot("10:00", "12:00"),
            new Slot("13:00", "15:00"), new Slot("15:00", "17:00"));

    public ParentChildrenService(JdbcTemplate jdbc, ParentContext ctx) {
        this.jdbc = jdbc;
        this.ctx = ctx;
    }

    /* ---------- GET /api/parent/children ---------- */
    public ParentChildrenDto list() {
        ParentContext.ParentInfo p = ctx.current();

        List<ChildFull> children = jdbc.query("""
                SELECT s.id, s.student_id, s.grade, s.rfid_tag, s.created_at,
                       u.full_name, u.email,
                       (SELECT COUNT(*) FROM attendance a WHERE a.student_id = s.id) AS total,
                       (SELECT COUNT(*) FROM attendance a
                        WHERE a.student_id = s.id AND a.status='Present') AS present
                FROM parent_child pc
                JOIN students s ON s.id = pc.student_id
                JOIN users u ON u.id = s.user_id
                WHERE pc.parent_id = ?
                ORDER BY u.full_name
                """,
                (rs, i) -> {
                    Long studentId = rs.getLong("id");
                    int total = rs.getInt("total");
                    int present = rs.getInt("present");
                    double pct = total == 0 ? 0.0 : round1(present * 100.0 / total);

                    Date d = rs.getDate("created_at");
                    String joined = d == null ? "—"
                            : d.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"));

                    List<String> subjects = jdbc.queryForList("""
                            SELECT DISTINCT s.name
                            FROM enrollments en
                            JOIN classes c ON c.id = en.class_id
                            JOIN subjects s ON s.id = c.subject_id
                            WHERE en.student_id = ? AND en.is_active = 1
                            ORDER BY s.name
                            """, String.class, studentId);

                    int classCount = subjects.size();

                    LatestGrade lg = latestGrade(studentId);

                    return new ChildFull(
                            studentId,
                            rs.getString("student_id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("grade"),
                            rs.getString("rfid_tag"),
                            joined,
                            pct, total, present,
                            classCount, subjects,
                            lg.grade, lg.pct);
                },
                p.id());

        ParentChildrenDto.ParentSummary summary =
                new ParentChildrenDto.ParentSummary(p.id(), p.nic(), p.fullName());

        return new ParentChildrenDto(summary, children);
    }

    /* ---------- GET /api/parent/child/{id} ---------- */
    public ParentChildDetailDto detail(Long childId) {
        ParentContext.ParentInfo p = ctx.current();
        ctx.assertChild(p.id(), childId);

        ChildSummary child = jdbc.queryForObject("""
                SELECT s.id, s.student_id, s.grade, u.full_name, u.email
                FROM students s
                JOIN users u ON u.id = s.user_id
                WHERE s.id = ?
                """,
                (rs, i) -> new ChildSummary(
                        rs.getLong("id"),
                        rs.getString("student_id"),
                        rs.getString("full_name"),
                        rs.getString("grade"),
                        rs.getString("email")),
                childId);

        // ---- Timetable ----
        List<TimetableEntry> timetable = jdbc.query("""
                SELECT t.day_of_week, t.start_time, t.end_time,
                       s.name AS subject, u.full_name AS teacher, r.name AS room
                FROM timetable t
                JOIN classes  c ON c.id = t.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN teachers te ON te.id = t.teacher_id
                JOIN users u ON u.id = te.user_id
                JOIN rooms r ON r.id = t.room_id
                JOIN enrollments en ON en.class_id = c.id
                WHERE en.student_id = ? AND en.is_active = 1
                ORDER BY t.start_time
                """,
                (rs, i) -> new TimetableEntry(
                        rs.getString("day_of_week"),
                        trim(rs.getString("start_time")),
                        trim(rs.getString("end_time")),
                        rs.getString("subject"),
                        rs.getString("teacher"),
                        rs.getString("room")),
                childId);

        // Derive slots
        Set<String> seen = new LinkedHashSet<>();
        List<Slot> slots = new ArrayList<>();
        for (TimetableEntry e : timetable) {
            String k = e.start() + "|" + e.end();
            if (seen.add(k)) slots.add(new Slot(e.start(), e.end()));
        }
        if (slots.isEmpty()) slots = new ArrayList<>(DEFAULT_SLOTS);
        else slots.sort(Comparator.comparing(Slot::start));

        List<String> days = DAY_ORDER.subList(0, 6);

        // ---- Attendance ----
        AttendanceAgg agg = jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN status='Present' THEN 1 ELSE 0 END) AS present,
                       SUM(CASE WHEN status='Absent'  THEN 1 ELSE 0 END) AS absent,
                       SUM(CASE WHEN status='Late'    THEN 1 ELSE 0 END) AS late
                FROM attendance WHERE student_id = ?
                """,
                (rs, i) -> new AttendanceAgg(
                        rs.getInt("total"),
                        rs.getInt("present"),
                        rs.getInt("absent"),
                        rs.getInt("late")),
                childId);

        double attPct = agg.total() == 0 ? 0.0
                : round1(agg.present() * 100.0 / agg.total());

        List<SubjectAttendance> bySubject = jdbc.query("""
                SELECT s.name AS subject,
                       COUNT(*) AS total,
                       SUM(CASE WHEN a.status='Present' THEN 1 ELSE 0 END) AS present,
                       SUM(CASE WHEN a.status='Absent'  THEN 1 ELSE 0 END) AS absent,
                       SUM(CASE WHEN a.status='Late'    THEN 1 ELSE 0 END) AS late
                FROM attendance a
                JOIN classes  c ON c.id = a.class_id
                JOIN subjects s ON s.id = c.subject_id
                WHERE a.student_id = ?
                GROUP BY s.name
                """,
                (rs, i) -> {
                    int t = rs.getInt("total");
                    int pr = rs.getInt("present");
                    double pct = t == 0 ? 0.0 : round1(pr * 100.0 / t);
                    return new SubjectAttendance(
                            rs.getString("subject"),
                            t, pr,
                            rs.getInt("absent"),
                            rs.getInt("late"),
                            pct);
                },
                childId);

        bySubject = bySubject.stream()
                .sorted((a, b) -> Double.compare(b.pct(), a.pct()))
                .collect(Collectors.toList());

        AttendanceBlock attendanceBlock = new AttendanceBlock(
                agg.total(), agg.present(), agg.absent(), agg.late(),
                attPct, bySubject);

        // ---- Exams ----
        List<ExamRow> exams = jdbc.query("""
                SELECT er.marks_obtained,
                       e.id, e.title, e.total_marks, e.created_at,
                       s.name AS subject,
                       u.full_name AS teacher
                FROM exam_results er
                JOIN exams e ON e.id = er.exam_id
                JOIN classes c ON c.id = e.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN users u ON u.id = e.created_by
                WHERE er.student_id = ? AND e.is_published = 1
                ORDER BY e.created_at DESC
                """,
                (rs, i) -> {
                    double marks = rs.getBigDecimal("marks_obtained").doubleValue();
                    int total = rs.getInt("total_marks");
                    double pct = total == 0 ? 0.0 : round1(marks * 100.0 / total);
                    Timestamp ts = rs.getTimestamp("created_at");
                    String date = ts == null ? "—"
                            : ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                    return new ExamRow(
                            rs.getLong("id"),
                            rs.getString("subject"),
                            rs.getString("title"),
                            rs.getString("teacher"),
                            date,
                            round1(marks),
                            total,
                            pct,
                            gradeOf(pct));
                },
                childId);

        return new ParentChildDetailDto(child, days, slots, timetable, attendanceBlock, exams);
    }

    /* ---------- helpers ---------- */
    private LatestGrade latestGrade(Long studentId) {
        return jdbc.query("""
                SELECT TOP 1 er.marks_obtained, e.total_marks
                FROM exam_results er
                JOIN exams e ON e.id = er.exam_id
                WHERE er.student_id = ? AND e.is_published = 1
                ORDER BY e.created_at DESC
                """,
                rs -> {
                    if (!rs.next()) return new LatestGrade("—", 0.0);
                    double marks = rs.getBigDecimal("marks_obtained").doubleValue();
                    int total = rs.getInt("total_marks");
                    double pct = total == 0 ? 0.0 : round1(marks * 100.0 / total);
                    return new LatestGrade(gradeOf(pct), pct);
                },
                studentId);
    }

    private static String gradeOf(double pct) {
        if (pct >= 75) return "A";
        if (pct >= 65) return "B";
        if (pct >= 55) return "C";
        if (pct >= 35) return "S";
        return "F";
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }

    private static String trim(String t) {
        if (t == null) return "";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private record LatestGrade(String grade, double pct) {}
    private record AttendanceAgg(int total, int present, int absent, int late) {}
}