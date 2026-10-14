package com.apexinstitute.dto;

import java.util.List;

public class StudentExamsDto {

    private final StudentSummary student;
    private final Stats stats;
    private final List<SubjectFilter> subjects;
    private final List<ExamRow> exams;

    public StudentExamsDto(StudentSummary student,
                           Stats stats,
                           List<SubjectFilter> subjects,
                           List<ExamRow> exams) {
        this.student = student;
        this.stats = stats;
        this.subjects = subjects;
        this.exams = exams;
    }

    public StudentSummary getStudent() { return student; }
    public Stats getStats() { return stats; }
    public List<SubjectFilter> getSubjects() { return subjects; }
    public List<ExamRow> getExams() { return exams; }

    public record StudentSummary(Long id, String studentId, String fullName, String grade) {}

    public record Stats(int totalExams,
                        double averagePct,
                        String averageGrade,
                        String bestSubject,
                        double bestSubjectPct,
                        String overallStanding) {}

    public record SubjectFilter(String subject, int count) {}

    public record ExamRow(Long id,
                          String subject,
                          String title,
                          String className,
                          String teacher,
                          String date,
                          double marks,
                          int totalMarks,
                          double percentage,
                          String grade) {}
}