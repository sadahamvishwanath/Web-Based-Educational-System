package com.apexinstitute.service;

import com.apexinstitute.dto.StudentMaterialsDto;
import com.apexinstitute.dto.StudentMaterialsDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MaterialsService {

    private final JdbcTemplate jdbc;

    public MaterialsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public StudentMaterialsDto forCurrentStudent() {

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

        // Fetch every material for every class this student is enrolled in
        List<MaterialItem> items = jdbc.query("""
                SELECT cf.id,
                       cf.file_name,
                       cf.file_type,
                       cf.stored_path,
                       cf.uploaded_at,
                       s.name AS subject,
                       c.name AS class_name,
                       c.grade,
                       u.full_name AS uploader
                FROM class_files cf
                JOIN classes   c ON c.id = cf.class_id
                JOIN subjects  s ON s.id = c.subject_id
                JOIN users     u ON u.id = cf.uploaded_by
                JOIN enrollments en ON en.class_id = c.id
                WHERE en.student_id = ? AND en.is_active = 1
                ORDER BY cf.uploaded_at DESC
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("uploaded_at");
                    LocalDateTime dt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    return new MaterialItem(
                            rs.getLong("id"),
                            rs.getString("file_name"),
                            rs.getString("file_type"),
                            rs.getString("subject"),
                            rs.getString("class_name"),
                            rs.getString("grade"),
                            rs.getString("uploader"),
                            humanTime(dt),
                            rs.getString("stored_path"));
                },
                studentPk);

        int notes = (int) items.stream().filter(x -> "note".equals(x.type())).count();
        int videos = (int) items.stream().filter(x -> "video".equals(x.type())).count();

        Stats stats = new Stats(items.size(), notes, videos);

        // Subject filter counts
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MaterialItem m : items) counts.merge(m.subject(), 1, Integer::sum);
        List<SubjectFilter> subjects = counts.entrySet().stream()
                .map(e -> new SubjectFilter(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(SubjectFilter::subject))
                .collect(Collectors.toList());

        return new StudentMaterialsDto(student, stats, subjects, items);
    }

    private static String humanTime(LocalDateTime dt) {
        LocalDateTime now = LocalDateTime.now();
        long days = java.time.Duration.between(dt, now).toDays();
        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        if (days < 30) return (days / 7) + "w ago";
        return dt.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
    }
}