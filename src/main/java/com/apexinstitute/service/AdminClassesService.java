package com.apexinstitute.service;

import com.apexinstitute.dto.AdminClassesDto;
import com.apexinstitute.dto.AdminClassesDto.ClassItem;
import com.apexinstitute.dto.AdminClassesDto.Stats;
import com.apexinstitute.dto.AdminClassesDto.SubjectOption;
import com.apexinstitute.dto.CreateClassRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminClassesService {

    private final JdbcTemplate jdbc;

    public AdminClassesService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- GET /api/admin/classes ---------- */
    public AdminClassesDto list() {

        List<ClassItem> classes = jdbc.query("""
                SELECT c.id, c.name, c.grade, c.is_active,
                       s.name AS subject,
                       (SELECT COUNT(*) FROM enrollments e
                        WHERE e.class_id = c.id AND e.is_active = 1) AS students,
                       (SELECT COUNT(*) FROM timetable t
                        WHERE t.class_id = c.id) AS slots
                FROM classes c
                JOIN subjects s ON s.id = c.subject_id
                ORDER BY c.grade, s.name
                """,
                (rs, i) -> new ClassItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject"),
                        rs.getString("grade"),
                        rs.getInt("students"),
                        rs.getInt("slots"),
                        rs.getBoolean("is_active")));

        List<SubjectOption> subjects = jdbc.query("""
                SELECT id, name FROM subjects ORDER BY name
                """,
                (rs, i) -> new SubjectOption(
                        rs.getLong("id"),
                        rs.getString("name")));

        int active   = (int) classes.stream().filter(ClassItem::isActive).count();
        int inactive = classes.size() - active;
        int students = classes.stream().mapToInt(ClassItem::students).sum();

        Stats stats = new Stats(classes.size(), active, inactive, students);

        return new AdminClassesDto(stats, classes, subjects);
    }

    /* ---------- POST /api/admin/classes ---------- */
    @Transactional
    public Long create(CreateClassRequest req) {
        String name = req.getName().trim();

        // Name must be unique
        Integer dupName = jdbc.queryForObject("""
                SELECT COUNT(*) FROM classes WHERE LOWER(name) = LOWER(?)
                """, Integer.class, name);
        if (dupName != null && dupName > 0)
            throw new RuntimeException("A class with this name already exists");

        // Subject must exist
        Integer subjExists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM subjects WHERE id = ?
                """, Integer.class, req.getSubjectId());
        if (subjExists == null || subjExists == 0)
            throw new RuntimeException("Subject not found");

        jdbc.update("""
                INSERT INTO classes
                    (name, grade, subject_id, is_active, created_at, updated_at)
                VALUES (?, ?, ?, 1, SYSDATETIME(), SYSDATETIME())
                """,
                name, req.getGrade(), req.getSubjectId());

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM classes ORDER BY id DESC", Long.class);
    }

    /* ---------- PATCH /api/admin/classes/{id}/toggle ---------- */
    @Transactional
    public void toggle(Long id) {
        int updated = jdbc.update("""
                UPDATE classes
                SET is_active = CASE WHEN is_active = 1 THEN 0 ELSE 1 END,
                    updated_at = SYSDATETIME()
                WHERE id = ?
                """, id);
        if (updated == 0) throw new RuntimeException("Class not found");
    }

    /* ---------- DELETE /api/admin/classes/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        // Refuse if any active enrollments
        Integer enrolled = jdbc.queryForObject("""
                SELECT COUNT(*) FROM enrollments
                WHERE class_id = ? AND is_active = 1
                """, Integer.class, id);
        if (enrolled != null && enrolled > 0)
            throw new RuntimeException(
                    "Cannot delete — " + enrolled + " student" + (enrolled == 1 ? "" : "s")
                            + " currently enrolled");

        // Refuse if any timetable slot uses it
        Integer slots = jdbc.queryForObject("""
                SELECT COUNT(*) FROM timetable WHERE class_id = ?
                """, Integer.class, id);
        if (slots != null && slots > 0)
            throw new RuntimeException(
                    "Cannot delete — used in " + slots + " timetable slot"
                            + (slots == 1 ? "" : "s"));

        // Cascade-delete related rows (exam_results, exam_questions, exams, class_files)
        // via ON DELETE CASCADE, then delete the class itself
        int deleted = jdbc.update("DELETE FROM classes WHERE id = ?", id);
        if (deleted == 0) throw new RuntimeException("Class not found");
    }
}