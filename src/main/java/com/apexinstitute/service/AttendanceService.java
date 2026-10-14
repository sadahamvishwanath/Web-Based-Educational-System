package com.apexinstitute.service;

import com.apexinstitute.dto.StudentAttendanceDto;
import com.apexinstitute.dto.StudentAttendanceDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final JdbcTemplate jdbc;

    public AttendanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public StudentAttendanceDto forCurrentStudent() {

        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

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

        List<LogEntry> all = jdbc.query("""
                SELECT a.[date],
                       a.status,
                       s.name AS subject,
                       u.full_name AS teacher
                FROM attendance a
                JOIN classes  c ON c.id = a.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN teachers t ON t.id = (
                    SELECT TOP 1 teacher_id FROM timetable
                    WHERE class_id = a.class_id
                    ORDER BY start_time
                )
                JOIN users u ON u.id = t.user_id
                WHERE a.student_id = ?
                ORDER BY a.[date] DESC, c.name
                """,
                (rs, i) -> {
                    Date sqlDate = rs.getDate("date");
                    LocalDate d = sqlDate == null ? LocalDate.now() : sqlDate.toLocalDate();
                    return new LogEntry(
                            d.toString(),
                            String.format("%02d", d.getDayOfMonth()),
                            d.format(DateTimeFormatter.ofPattern("MMM")),
                            rs.getString("subject"),
                            rs.getString("teacher"),
                            rs.getString("status"),
                            "—");
                },
                studentPk);

        int total = all.size();
        int present = 0, absent = 0, late = 0;
        for (LogEntry e : all) {
            switch (e.status()) {
                case "Present" -> present++;
                case "Absent"  -> absent++;
                case "Late"    -> late++;
            }
        }
        double pct = total == 0 ? 0.0 : round1(present * 100.0 / total);

        int streak = 0;
        for (LogEntry e : all) {
            if ("Present".equals(e.status())) streak++;
            else break;
        }

        Stats stats = new Stats(total, present, absent, late, pct, streak);

        Map<String, int[]> bySubj = new LinkedHashMap<>();
        for (LogEntry e : all) {
            bySubj.computeIfAbsent(e.subject(), k -> new int[4]);
            int[] arr = bySubj.get(e.subject());
            arr[0]++;
            switch (e.status()) {
                case "Present" -> arr[1]++;
                case "Absent"  -> arr[2]++;
                case "Late"    -> arr[3]++;
            }
        }

        List<SubjectBreakdown> breakdown = bySubj.entrySet().stream()
                .map(en -> {
                    int[] a = en.getValue();
                    double p = a[0] == 0 ? 0.0 : round1(a[1] * 100.0 / a[0]);
                    return new SubjectBreakdown(en.getKey(), a[0], a[1], a[2], a[3], p);
                })
                .sorted((x, y) -> Double.compare(y.pct(), x.pct()))
                .collect(Collectors.toList());

        return new StudentAttendanceDto(student, stats, breakdown, all);
    }

    private static double round1(double n) {
        return Math.round(n * 10.0) / 10.0;
    }
}