package com.apexinstitute.service;

import com.apexinstitute.dto.StudentTimetableDto;
import com.apexinstitute.dto.StudentTimetableDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TimetableService {

    private final JdbcTemplate jdbc;

    private static final List<String> DAY_ORDER = List.of(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private static final List<Slot> DEFAULT_SLOTS = List.of(
            new Slot("08:00", "10:00"),
            new Slot("10:00", "12:00"),
            new Slot("13:00", "15:00"),
            new Slot("15:00", "17:00"));

    public TimetableService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public StudentTimetableDto forCurrentStudent() {

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

        List<Entry> entries = jdbc.query("""
                SELECT t.day_of_week,
                       t.start_time,
                       t.end_time,
                       c.id   AS class_id,
                       c.name AS class_name,
                       c.grade,
                       s.name AS subject,
                       u.full_name AS teacher,
                       r.name AS room
                FROM timetable t
                JOIN classes   c  ON c.id = t.class_id
                JOIN subjects  s  ON s.id = c.subject_id
                JOIN teachers  te ON te.id = t.teacher_id
                JOIN users     u  ON u.id = te.user_id
                JOIN rooms     r  ON r.id = t.room_id
                JOIN enrollments en ON en.class_id = c.id
                WHERE en.student_id = ? AND en.is_active = 1
                ORDER BY
                    CASE t.day_of_week
                        WHEN 'Monday'    THEN 1
                        WHEN 'Tuesday'   THEN 2
                        WHEN 'Wednesday' THEN 3
                        WHEN 'Thursday'  THEN 4
                        WHEN 'Friday'    THEN 5
                        WHEN 'Saturday'  THEN 6
                        WHEN 'Sunday'    THEN 7
                        ELSE 8
                    END,
                    t.start_time
                """,
                (rs, i) -> new Entry(
                        rs.getString("day_of_week"),
                        trim(rs.getString("start_time")),
                        trim(rs.getString("end_time")),
                        rs.getLong("class_id"),
                        rs.getString("class_name"),
                        rs.getString("grade"),
                        rs.getString("subject"),
                        rs.getString("teacher"),
                        rs.getString("room")),
                studentPk);

        Set<String> seen = new LinkedHashSet<>();
        List<Slot> slots = new ArrayList<>();
        for (Entry e : entries) {
            String key = e.start() + "|" + e.end();
            if (seen.add(key)) slots.add(new Slot(e.start(), e.end()));
        }
        if (slots.isEmpty()) {
            slots = new ArrayList<>(DEFAULT_SLOTS);
        } else {
            slots.sort(Comparator.comparing(Slot::start));
        }

        List<String> days = DAY_ORDER.subList(0, 6);

        return new StudentTimetableDto(student, days, slots, entries);
    }

    private static String trim(String t) {
        if (t == null) return "";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }
}