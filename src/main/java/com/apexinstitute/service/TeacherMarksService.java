package com.apexinstitute.service;

import com.apexinstitute.dto.MarksRosterDto;
import com.apexinstitute.dto.MarksRosterDto.StudentMark;
import com.apexinstitute.dto.SaveMarksRequest;
import com.apexinstitute.dto.TeacherExamsDto;
import com.apexinstitute.dto.TeacherExamsDto.ExamItem;
import com.apexinstitute.dto.TeacherExamsDto.TeacherSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.apexinstitute.dto.ExamQuestionDto;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TeacherMarksService {

    private final JdbcTemplate jdbc;

    public TeacherMarksService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- Teacher identity: carries BOTH ids ---------- */
    private TeacherCtx currentTeacher() {
        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());
        TeacherCtx t = jdbc.queryForObject("""
                SELECT t.id AS teacher_id,
                       t.user_id,
                       u.full_name
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

    /* ---------- GET /api/teacher/exams ---------- */
    public TeacherExamsDto exams() {
        TeacherCtx teacher = currentTeacher();

        List<ExamItem> list = jdbc.query("""
                SELECT e.id, e.title, e.total_marks, e.is_published,
                       c.name AS class_name, c.grade,
                       s.name AS subject,
                       (SELECT COUNT(*) FROM exam_results er WHERE er.exam_id = e.id) AS marked,
                       (SELECT COUNT(*) FROM enrollments en
                        WHERE en.class_id = e.class_id AND en.is_active = 1) AS total,
                       (SELECT COUNT(*) FROM exam_questions q WHERE q.exam_id = e.id) AS question_count
                FROM exams e
                JOIN classes  c ON c.id = e.class_id
                JOIN subjects s ON s.id = c.subject_id
                WHERE e.created_by = ?
                ORDER BY e.created_at DESC
                """,
                (rs, i) -> new ExamItem(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("class_name"),
                        rs.getString("subject"),
                        rs.getString("grade"),
                        rs.getInt("total_marks"),
                        rs.getBoolean("is_published"),
                        rs.getInt("marked"),
                        rs.getInt("total"),
                        rs.getInt("question_count")),
                teacher.userId());

        TeacherSummary summary = new TeacherSummary(
                teacher.teacherId(),
                "T" + String.format("%04d", teacher.teacherId()),
                teacher.fullName());

        return new TeacherExamsDto(summary, list);
    }

    /* ---------- GET /api/teacher/exams/{id}/marks ---------- */
    public MarksRosterDto roster(Long examId) {
        TeacherCtx teacher = currentTeacher();

        ExamMeta meta = jdbc.queryForObject("""
                SELECT e.id, e.title, e.total_marks, e.is_published, e.class_id,
                       c.name AS class_name, c.grade, s.name AS subject
                FROM exams e
                JOIN classes  c ON c.id = e.class_id
                JOIN subjects s ON s.id = c.subject_id
                WHERE e.id = ? AND e.created_by = ?
                """,
                (rs, i) -> new ExamMeta(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getInt("total_marks"),
                        rs.getBoolean("is_published"),
                        rs.getLong("class_id"),
                        rs.getString("class_name"),
                        rs.getString("grade"),
                        rs.getString("subject")),
                examId, teacher.userId());

        if (meta == null) throw new RuntimeException("Exam not found or not created by you");

        List<StudentMark> roster = jdbc.query("""
                SELECT s.id AS student_id, s.student_id AS student_code,
                       u.full_name, er.marks_obtained
                FROM enrollments en
                JOIN students s ON s.id = en.student_id
                JOIN users    u ON u.id = s.user_id
                LEFT JOIN exam_results er
                       ON er.student_id = s.id AND er.exam_id = ?
                WHERE en.class_id = ? AND en.is_active = 1
                ORDER BY u.full_name
                """,
                (rs, i) -> {
                    BigDecimal bd = rs.getBigDecimal("marks_obtained");
                    Double marks = bd == null ? null : bd.doubleValue();
                    return new StudentMark(
                            rs.getLong("student_id"),
                            rs.getString("student_code"),
                            rs.getString("full_name"),
                            marks);
                },
                examId, meta.classId());

        return new MarksRosterDto(
                meta.id, meta.title, meta.className, meta.subject,
                meta.grade, meta.totalMarks, meta.published, roster);
    }

    /* ---------- POST /api/teacher/exams/{id}/marks ---------- */
    @Transactional
    public int save(Long examId, SaveMarksRequest req) {
        TeacherCtx teacher = currentTeacher();

        ExamMeta meta = jdbc.queryForObject("""
                SELECT e.id, e.total_marks, e.class_id
                FROM exams e
                WHERE e.id = ? AND e.created_by = ?
                """,
                (rs, i) -> new ExamMeta(
                        rs.getLong("id"),
                        null,
                        rs.getInt("total_marks"),
                        false,
                        rs.getLong("class_id"),
                        null, null, null),
                examId, teacher.userId());

        if (meta == null) throw new RuntimeException("Exam not found or not created by you");

        int written = 0;
        for (SaveMarksRequest.Entry e : req.getEntries()) {

            if (e.getMarks() == null) {
                int deleted = jdbc.update(
                        "DELETE FROM exam_results WHERE exam_id = ? AND student_id = ?",
                        examId, e.getStudentId());
                if (deleted > 0) written++;
                continue;
            }

            if (e.getMarks() < 0 || e.getMarks() > meta.totalMarks())
                throw new RuntimeException("Marks must be between 0 and " + meta.totalMarks());

            Integer enrolled = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM enrollments
                    WHERE class_id = ? AND student_id = ? AND is_active = 1
                    """, Integer.class, meta.classId(), e.getStudentId());
            if (enrolled == null || enrolled == 0)
                throw new RuntimeException("Student " + e.getStudentId() + " not enrolled");

            Integer updated = jdbc.update("""
                    UPDATE exam_results
                    SET marks_obtained = ?, entered_by = ?, entered_at = SYSDATETIME()
                    WHERE exam_id = ? AND student_id = ?
                    """, e.getMarks(), teacher.userId(), examId, e.getStudentId());

            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO exam_results
                            (exam_id, student_id, marks_obtained, entered_by, entered_at)
                        VALUES (?, ?, ?, ?, SYSDATETIME())
                        """, examId, e.getStudentId(), e.getMarks(), teacher.userId());
            }
            written++;
        }
        return written;
    }

    /* ---------- POST /api/teacher/exams/{id}/publish ---------- */
    @Transactional
    public void publish(Long examId) {
        TeacherCtx teacher = currentTeacher();
        int updated = jdbc.update("""
                UPDATE exams SET is_published = 1
                WHERE id = ? AND created_by = ?
                """, examId, teacher.userId());
        if (updated == 0) throw new RuntimeException("Exam not found or not yours");
    }

    /* ---------- POST /api/teacher/exams ---------- */
    @Transactional
    public Long createExam(com.apexinstitute.dto.CreateExamRequest req) {
        TeacherCtx teacher = currentTeacher();

        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM timetable
                WHERE class_id = ? AND teacher_id = ?
                """, Integer.class, req.getClassId(), teacher.teacherId());
        if (owns == null || owns == 0)
            throw new RuntimeException("Class not taught by you");

        jdbc.update("""
                INSERT INTO exams (class_id, created_by, title, total_marks, is_published, created_at)
                VALUES (?, ?, ?, ?, ?, SYSDATETIME())
                """,
                req.getClassId(),
                teacher.userId(),
                req.getTitle(),
                req.getTotalMarks(),
                req.isPublish() ? 1 : 0);

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM exams WHERE created_by = ? ORDER BY id DESC",
                Long.class, teacher.userId());
    }

    /* ---------- GET /api/teacher/exams/{id}/questions ---------- */
    public List<com.apexinstitute.dto.ExamQuestionDto> questions(Long examId) {
        TeacherCtx teacher = currentTeacher();

        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exams WHERE id = ? AND created_by = ?
                """, Integer.class, examId, teacher.userId());
        if (owns == null || owns == 0)
            throw new RuntimeException("Exam not found or not created by you");

        return jdbc.query("""
                SELECT id, question_text, option_a, option_b, option_c, option_d,
                       correct_answer, order_no
                FROM exam_questions
                WHERE exam_id = ?
                ORDER BY order_no
                """,
                (rs, i) -> new com.apexinstitute.dto.ExamQuestionDto(
                        rs.getLong("id"),
                        rs.getString("question_text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        rs.getString("correct_answer"),
                        rs.getInt("order_no")),
                examId);
    }

    /* ---------- POST /api/teacher/exams/{id}/questions ---------- */
    @Transactional
    public Long addQuestion(Long examId, com.apexinstitute.dto.CreateQuestionRequest req) {
        TeacherCtx teacher = currentTeacher();

        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exams WHERE id = ? AND created_by = ?
                """, Integer.class, examId, teacher.userId());
        if (owns == null || owns == 0)
            throw new RuntimeException("Exam not found or not created by you");

        Integer nextOrder = jdbc.queryForObject("""
                SELECT COALESCE(MAX(order_no), 0) + 1 FROM exam_questions WHERE exam_id = ?
                """, Integer.class, examId);

        jdbc.update("""
                INSERT INTO exam_questions
                    (exam_id, question_text, option_a, option_b, option_c, option_d,
                     correct_answer, order_no)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                examId,
                req.getText(),
                req.getOptionA(),
                req.getOptionB(),
                req.getOptionC(),
                req.getOptionD(),
                req.getCorrect(),
                nextOrder == null ? 1 : nextOrder);

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM exam_questions WHERE exam_id = ? ORDER BY id DESC",
                Long.class, examId);
    }

    /* ---------- DELETE /api/teacher/exams/{examId}/questions/{qId} ---------- */
    @Transactional
    public void deleteQuestion(Long examId, Long questionId) {
        TeacherCtx teacher = currentTeacher();

        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exams WHERE id = ? AND created_by = ?
                """, Integer.class, examId, teacher.userId());
        if (owns == null || owns == 0)
            throw new RuntimeException("Exam not found or not created by you");

        int deleted = jdbc.update(
                "DELETE FROM exam_questions WHERE id = ? AND exam_id = ?",
                questionId, examId);
        if (deleted == 0) throw new RuntimeException("Question not found");
    }

    /* ---------- DELETE /api/teacher/exams/{examId} ---------- */
    @Transactional
    public void deleteExam(Long examId) {
        TeacherCtx teacher = currentTeacher();

        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exams WHERE id = ? AND created_by = ?
                """, Integer.class, examId, teacher.userId());
        if (owns == null || owns == 0)
            throw new RuntimeException("Exam not found or not created by you");

        int deleted = jdbc.update("DELETE FROM exams WHERE id = ?", examId);
        if (deleted == 0) throw new RuntimeException("Exam not found");
    }

    /* ---------- PATCH /api/teacher/exams/{examId}  (NEW) ---------- */
    @Transactional
    public void renameExam(Long examId, String newTitle) {
        TeacherCtx teacher = currentTeacher();

        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exams WHERE id = ? AND created_by = ?
                """, Integer.class, examId, teacher.userId());
        if (owns == null || owns == 0)
            throw new RuntimeException("Exam not found or not created by you");

        if (newTitle == null || newTitle.isBlank())
            throw new RuntimeException("Title cannot be empty");

        String trimmed = newTitle.trim();
        if (trimmed.length() > 200)
            throw new RuntimeException("Title must be 200 characters or fewer");

        jdbc.update("UPDATE exams SET title = ? WHERE id = ?", trimmed, examId);
    }

    /* ---------- internal helpers ---------- */
    private record TeacherCtx(Long teacherId, Long userId, String fullName) {}
    private record ExamMeta(Long id, String title, int totalMarks, boolean published,
                            Long classId, String className, String grade, String subject) {}
}