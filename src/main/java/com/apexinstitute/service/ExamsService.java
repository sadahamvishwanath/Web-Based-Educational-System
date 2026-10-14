package com.apexinstitute.service;

import com.apexinstitute.dto.StudentExamsDto;
import com.apexinstitute.dto.StudentExamsDto.*;
import com.apexinstitute.dto.ExamQuestionDto;
import com.apexinstitute.dto.ExamTakeQuestionDto;
import com.apexinstitute.dto.StudentAvailableExamsDto;
import com.apexinstitute.dto.StudentAvailableExamsDto.AvailableExam;
import com.apexinstitute.dto.SubmitExamRequest;
import com.apexinstitute.dto.SubmitExamResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamsService {

    private final JdbcTemplate jdbc;

    public ExamsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String gradeOf(double pct) {
        if (pct >= 75) return "A";
        if (pct >= 65) return "B";
        if (pct >= 55) return "C";
        if (pct >= 35) return "S";
        return "F";
    }

    /* ---------- Who am I? ---------- */
    private Long currentStudentPk() {
        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());
        Long pk = jdbc.query(
                "SELECT id FROM students WHERE user_id = ?",
                rs -> rs.next() ? rs.getLong("id") : null,
                userId);
        if (pk == null) throw new RuntimeException("Student record not found");
        return pk;
    }

    /* ============================================================
       EXISTING — student's graded results
       ============================================================ */
    public StudentExamsDto forCurrentStudent() {

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

        List<ExamRow> rows = jdbc.query("""
                SELECT er.marks_obtained,
                       e.id           AS exam_id,
                       e.title,
                       e.total_marks,
                       e.created_at,
                       s.name         AS subject,
                       c.name         AS class_name,
                       u.full_name    AS teacher
                FROM exam_results er
                JOIN exams    e ON e.id = er.exam_id
                JOIN classes  c ON c.id = e.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN users    u ON u.id = e.created_by
                WHERE er.student_id = ? AND e.is_published = 1
                ORDER BY e.created_at DESC
                """,
                (rs, i) -> {
                    double marks = rs.getDouble("marks_obtained");
                    int total = rs.getInt("total_marks");
                    double pct = total > 0 ? round1(marks * 100.0 / total) : 0.0;
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime dt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    return new ExamRow(
                            rs.getLong("exam_id"),
                            rs.getString("subject"),
                            rs.getString("title"),
                            rs.getString("class_name"),
                            rs.getString("teacher"),
                            dt.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            round1(marks),
                            total,
                            pct,
                            gradeOf(pct));
                },
                studentPk);

        Stats stats = computeStats(rows);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ExamRow r : rows) {
            counts.merge(r.subject(), 1, Integer::sum);
        }
        List<SubjectFilter> subjects = counts.entrySet().stream()
                .map(e -> new SubjectFilter(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(SubjectFilter::subject))
                .collect(Collectors.toList());

        return new StudentExamsDto(student, stats, subjects, rows);
    }

    /* ============================================================
       EXISTING — questions for a graded result (with correct shown)
       ============================================================ */
    public List<ExamQuestionDto> questionsForExam(Long examId) {

        Long studentPk = currentStudentPk();

        Integer ok = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM exam_results er
                JOIN exams e ON e.id = er.exam_id
                WHERE er.exam_id = ? AND er.student_id = ? AND e.is_published = 1
                """, Integer.class, examId, studentPk);
        if (ok == null || ok == 0)
            throw new RuntimeException("Exam not found or not yet published");

        return jdbc.query("""
                SELECT id, question_text, option_a, option_b, option_c, option_d,
                       correct_answer, order_no
                FROM exam_questions
                WHERE exam_id = ?
                ORDER BY order_no
                """,
                (rs, i) -> new ExamQuestionDto(
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

    /* ============================================================
       NEW #1 — exams the student can still take
       ============================================================ */
    public StudentAvailableExamsDto availableForCurrentStudent() {

        Long studentPk = currentStudentPk();

        List<AvailableExam> list = jdbc.query("""
                SELECT e.id, e.title, e.total_marks,
                       c.name  AS class_name,
                       s.name  AS subject,
                       u.full_name AS teacher,
                       (SELECT COUNT(*) FROM exam_questions q WHERE q.exam_id = e.id) AS question_count
                FROM exams e
                JOIN classes      c ON c.id = e.class_id
                JOIN subjects     s ON s.id = c.subject_id
                JOIN users        u ON u.id = e.created_by
                JOIN enrollments en ON en.class_id = e.class_id
                WHERE en.student_id = ? AND en.is_active = 1
                  AND e.is_published = 1
                  AND NOT EXISTS (
                      SELECT 1 FROM exam_results er
                      WHERE er.exam_id = e.id AND er.student_id = ?
                  )
                ORDER BY e.created_at DESC
                """,
                (rs, i) -> new AvailableExam(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("class_name"),
                        rs.getString("subject"),
                        rs.getString("teacher"),
                        rs.getInt("question_count"),
                        rs.getInt("total_marks")),
                studentPk, studentPk);

        return new StudentAvailableExamsDto(list);
    }

    /* ============================================================
       NEW #2 — questions for taking an exam (NO correct answer)
       ============================================================ */
    public List<ExamTakeQuestionDto> questionsForTaking(Long examId) {

        Long studentPk = currentStudentPk();

        // Exam must be published
        Integer published = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exams WHERE id = ? AND is_published = 1
                """, Integer.class, examId);
        if (published == null || published == 0)
            throw new RuntimeException("Exam not found or not published");

        // Student must be enrolled in its class
        Integer enrolled = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM exams e
                JOIN enrollments en ON en.class_id = e.class_id
                WHERE e.id = ? AND en.student_id = ? AND en.is_active = 1
                """, Integer.class, examId, studentPk);
        if (enrolled == null || enrolled == 0)
            throw new RuntimeException("You are not enrolled in this exam's class");

        // Student must NOT have already taken it
        Integer taken = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exam_results WHERE exam_id = ? AND student_id = ?
                """, Integer.class, examId, studentPk);
        if (taken != null && taken > 0)
            throw new RuntimeException("You have already taken this exam");

        return jdbc.query("""
                SELECT id, question_text, option_a, option_b, option_c, option_d, order_no
                FROM exam_questions
                WHERE exam_id = ?
                ORDER BY order_no
                """,
                (rs, i) -> new ExamTakeQuestionDto(
                        rs.getLong("id"),
                        rs.getString("question_text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        rs.getInt("order_no")),
                examId);
    }

    /* ============================================================
       NEW #3 — grade the exam and save the result
       ============================================================ */
    @Transactional
    public SubmitExamResponse submitExam(Long examId, SubmitExamRequest req) {

        Long studentPk = currentStudentPk();

        Map<String, Object> examMeta = jdbc.queryForMap("""
            SELECT id, total_marks, created_by, is_published
            FROM exams
            WHERE id = ?
            """, examId);

        if (examMeta.isEmpty())
            throw new RuntimeException("Exam not found");

        boolean published = Boolean.TRUE.equals(examMeta.get("is_published"));
        if (!published) throw new RuntimeException("Exam is not published yet");

        int totalMarks = ((Number) examMeta.get("total_marks")).intValue();
        Long teacherUserId = ((Number) examMeta.get("created_by")).longValue();

        Integer enrolled = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM exams e
            JOIN enrollments en ON en.class_id = e.class_id
            WHERE e.id = ? AND en.student_id = ? AND en.is_active = 1
            """, Integer.class, examId, studentPk);
        if (enrolled == null || enrolled == 0)
            throw new RuntimeException("You are not enrolled in this exam's class");

        Integer taken = jdbc.queryForObject("""
            SELECT COUNT(*) FROM exam_results WHERE exam_id = ? AND student_id = ?
            """, Integer.class, examId, studentPk);
        if (taken != null && taken > 0)
            throw new RuntimeException("You have already taken this exam");

        Map<Long, String> correctMap = new HashMap<>();
        jdbc.query("""
            SELECT id, correct_answer FROM exam_questions WHERE exam_id = ?
            """,
                rs -> {
                    correctMap.put(rs.getLong("id"), rs.getString("correct_answer"));
                },
                examId);

        if (correctMap.isEmpty())
            throw new RuntimeException("This exam has no questions yet");

        Map<Long, String> answers = new HashMap<>();
        if (req.getAnswers() != null) {
            for (SubmitExamRequest.Entry e : req.getAnswers()) {
                if (e.getQuestionId() == null) continue;
                String c = e.getChoice();
                if (c != null && !c.isBlank()) answers.put(e.getQuestionId(), c.trim().toUpperCase());
            }
        }

        int correct = 0, incorrect = 0, unanswered = 0;
        for (Map.Entry<Long, String> entry : correctMap.entrySet()) {
            String studentChoice = answers.get(entry.getKey());
            if (studentChoice == null) {
                unanswered++;
            } else if (studentChoice.equals(entry.getValue())) {
                correct++;
            } else {
                incorrect++;
            }
        }

        int totalQuestions = correctMap.size();
        double marksObtained = totalQuestions == 0
                ? 0
                : round1((correct * (double) totalMarks) / totalQuestions);
        double pct = totalMarks > 0 ? round1(marksObtained * 100.0 / totalMarks) : 0.0;
        String grade = gradeOf(pct);

        jdbc.update("""
            INSERT INTO exam_results
                (exam_id, student_id, marks_obtained, entered_by, entered_at, auto_graded)
            VALUES (?, ?, ?, ?, SYSDATETIME(), 1)
            """,
                examId, studentPk, marksObtained, teacherUserId);

        return new SubmitExamResponse(
                correct, incorrect, unanswered,
                totalQuestions, marksObtained, totalMarks, pct, grade);
    }

    /* ============================================================
       Stats helper (unchanged)
       ============================================================ */
    private Stats computeStats(List<ExamRow> rows) {
        if (rows.isEmpty()) {
            return new Stats(0, 0.0, null, null, 0.0, "No exams yet");
        }

        int totalExams = rows.size();
        double totalMarks = rows.stream().mapToDouble(ExamRow::marks).sum();
        double totalPossible = rows.stream().mapToInt(ExamRow::totalMarks).sum();
        double avgPct = totalPossible > 0 ? round1(totalMarks * 100.0 / totalPossible) : 0.0;

        Map<String, double[]> bySubject = new HashMap<>();
        for (ExamRow r : rows) {
            bySubject.computeIfAbsent(r.subject(), k -> new double[2]);
            bySubject.get(r.subject())[0] += r.marks();
            bySubject.get(r.subject())[1] += r.totalMarks();
        }
        String bestSubject = null;
        double bestPct = -1;
        for (Map.Entry<String, double[]> e : bySubject.entrySet()) {
            double pct = e.getValue()[1] > 0 ? e.getValue()[0] * 100.0 / e.getValue()[1] : 0;
            if (pct > bestPct) {
                bestPct = pct;
                bestSubject = e.getKey();
            }
        }

        String avgGrade = gradeOf(avgPct);
        String standing = avgPct >= 75 ? "Excellent"
                : avgPct >= 65 ? "Very good"
                : avgPct >= 55 ? "Good"
                : avgPct >= 35 ? "Needs work"
                : "At risk";

        return new Stats(totalExams, avgPct, avgGrade, bestSubject, round1(bestPct), standing);
    }

    private static double round1(double n) {
        return Math.round(n * 10.0) / 10.0;
    }
}