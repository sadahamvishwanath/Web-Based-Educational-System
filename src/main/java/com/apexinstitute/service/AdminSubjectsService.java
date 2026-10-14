package com.apexinstitute.service;

import com.apexinstitute.dto.AdminSubjectsDto;
import com.apexinstitute.dto.AdminSubjectsDto.SubjectItem;
import com.apexinstitute.dto.CreateSubjectRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminSubjectsService {

    private final JdbcTemplate jdbc;

    public AdminSubjectsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- GET /api/admin/subjects ---------- */
    public AdminSubjectsDto list() {

        List<SubjectItem> subjects = jdbc.query("""
                SELECT s.id, s.name,
                       (SELECT COUNT(*) FROM classes c WHERE c.subject_id = s.id) AS class_count,
                       (SELECT COUNT(DISTINCT grade) FROM classes c WHERE c.subject_id = s.id) AS grade_count
                FROM subjects s
                ORDER BY s.name
                """,
                (rs, i) -> new SubjectItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("class_count"),
                        rs.getInt("grade_count")));

        int inUse = (int) subjects.stream().filter(s -> s.classCount() > 0).count();
        return new AdminSubjectsDto(subjects, subjects.size(), inUse);
    }

    /* ---------- POST /api/admin/subjects ---------- */
    @Transactional
    public Long create(CreateSubjectRequest req) {
        String name = req.getName().trim();

        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM subjects WHERE LOWER(name) = LOWER(?)
                """, Integer.class, name);
        if (exists != null && exists > 0)
            throw new RuntimeException("Subject already exists");

        jdbc.update("INSERT INTO subjects (name) VALUES (?)", name);

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM subjects ORDER BY id DESC", Long.class);
    }

    /* ---------- DELETE /api/admin/subjects/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        // Check if any classes use this subject
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(*) FROM classes WHERE subject_id = ?
                """, Integer.class, id);
        if (used != null && used > 0)
            throw new RuntimeException(
                    "Cannot delete — " + used + " class" + (used == 1 ? "" : "es")
                            + " use this subject");

        int deleted = jdbc.update("DELETE FROM subjects WHERE id = ?", id);
        if (deleted == 0) throw new RuntimeException("Subject not found");
    }
}