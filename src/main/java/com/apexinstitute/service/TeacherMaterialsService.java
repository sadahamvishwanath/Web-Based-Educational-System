package com.apexinstitute.service;

import com.apexinstitute.dto.TeacherMaterialsDto;
import com.apexinstitute.dto.TeacherMaterialsDto.MaterialItem;
import com.apexinstitute.dto.TeacherMaterialsDto.Stats;
import com.apexinstitute.dto.TeacherMaterialsDto.TeacherSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class TeacherMaterialsService {

    private final JdbcTemplate jdbc;

    public TeacherMaterialsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- Teacher context: carries both IDs ---------- */
    private TeacherCtx currentTeacher() {
        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());
        TeacherCtx t = jdbc.queryForObject("""
                SELECT t.id AS teacher_id, t.user_id, u.full_name
                FROM teachers t
                JOIN users u ON u.id = t.user_id
                WHERE t.user_id = ?
                """,
                (rs, i) -> new TeacherCtx(
                        rs.getLong("teacher_id"),
                        rs.getLong("user_id"),
                        rs.getString("full_name")),
                userId);
        if (t == null) throw new RuntimeException("Teacher record not found");
        return t;
    }

    /* ---------- GET /api/teacher/materials ---------- */
    public TeacherMaterialsDto list() {
        TeacherCtx teacher = currentTeacher();

        List<MaterialItem> items = jdbc.query("""
                SELECT cf.id, cf.file_name, cf.file_type, cf.stored_path, cf.uploaded_at,
                       s.name AS subject, c.name AS class_name, c.grade
                FROM class_files cf
                JOIN classes  c ON c.id = cf.class_id
                JOIN subjects s ON s.id = c.subject_id
                WHERE cf.uploaded_by = ?
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
                            humanTime(dt),
                            "",
                            rs.getString("stored_path"));
                },
                teacher.userId());

        int notes = (int) items.stream().filter(m -> "note".equals(m.type())).count();
        int videos = (int) items.stream().filter(m -> "video".equals(m.type())).count();
        Stats stats = new Stats(items.size(), notes, videos);

        TeacherSummary summary = new TeacherSummary(
                teacher.teacherId(),
                "T" + String.format("%04d", teacher.teacherId()),
                teacher.fullName());

        return new TeacherMaterialsDto(summary, stats, items);
    }

    /* ---------- POST /api/teacher/materials ---------- */
    @Transactional
    public Long upload(Long classId, String fileType, MultipartFile file) throws IOException {
        TeacherCtx teacher = currentTeacher();

        // Class must belong to this teacher
        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM timetable
                WHERE class_id = ? AND teacher_id = ?
                """, Integer.class, classId, teacher.teacherId());
        if (owns == null || owns == 0)
            throw new RuntimeException("Class not taught by you");

        // File type check
        if (!"note".equals(fileType) && !"video".equals(fileType))
            throw new RuntimeException("Invalid file type");

        if (file == null || file.isEmpty())
            throw new RuntimeException("No file uploaded");

        String original = file.getOriginalFilename();
        if (original == null || original.isBlank())
            throw new RuntimeException("File has no name");

        String lower = original.toLowerCase();

        // Extension check per type
        if ("note".equals(fileType) && !lower.endsWith(".pdf"))
            throw new RuntimeException("Notes must be PDF files");
        if ("video".equals(fileType) && !(lower.endsWith(".mp4") || lower.endsWith(".mov")))
            throw new RuntimeException("Videos must be MP4 or MOV");

        // Save to disk under uploads/notes/ or uploads/videos/
        String ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        String unique = UUID.randomUUID().toString().replace("-", "") + ext;
        String subdir = "note".equals(fileType) ? "notes" : "videos";

        Path dir = Paths.get("uploads", subdir).toAbsolutePath();
        Files.createDirectories(dir);
        Path target = dir.resolve(unique);
        file.transferTo(target.toFile());

        String dbPath = "/uploads/" + subdir + "/" + unique;

        jdbc.update("""
                INSERT INTO class_files
                    (class_id, uploaded_by, file_name, stored_path, file_type, uploaded_at)
                VALUES (?, ?, ?, ?, ?, SYSDATETIME())
                """, classId, teacher.userId(), original, dbPath, fileType);

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM class_files WHERE uploaded_by = ? ORDER BY id DESC",
                Long.class, teacher.userId());
    }

    /* ---------- DELETE /api/teacher/materials/{id} ---------- */
    @Transactional
    public void delete(Long materialId) {
        TeacherCtx teacher = currentTeacher();

        String storedPath = jdbc.query("""
                SELECT stored_path FROM class_files
                WHERE id = ? AND uploaded_by = ?
                """, rs -> rs.next() ? rs.getString("stored_path") : null,
                materialId, teacher.userId());

        if (storedPath == null)
            throw new RuntimeException("Material not found or not yours");

        jdbc.update("DELETE FROM class_files WHERE id = ?", materialId);

        // Best-effort disk delete
        try {
            if (storedPath.startsWith("/uploads/")) {
                Path file = Paths.get(storedPath.substring(1));
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) { /* leave on disk; harmless */ }
    }

    /* ---------- helpers ---------- */
    private static String humanTime(LocalDateTime dt) {
        long days = Duration.between(dt, LocalDateTime.now()).toDays();
        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        if (days < 30) return (days / 7) + "w ago";
        return dt.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
    }

    private record TeacherCtx(Long teacherId, Long userId, String fullName) {}
}