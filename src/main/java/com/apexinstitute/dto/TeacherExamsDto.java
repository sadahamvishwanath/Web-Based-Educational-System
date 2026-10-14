package com.apexinstitute.dto;

import java.util.List;

public class TeacherExamsDto {

    private final TeacherSummary teacher;
    private final List<ExamItem> exams;

    public TeacherExamsDto(TeacherSummary teacher, List<ExamItem> exams) {
        this.teacher = teacher;
        this.exams = exams;
    }

    public TeacherSummary getTeacher() { return teacher; }
    public List<ExamItem> getExams() { return exams; }

    public record TeacherSummary(Long id, String teacherId, String fullName) {}

    public record ExamItem(Long id, String title, String className, String subject,
                           String grade, int totalMarks, boolean published,
                           int marked, int total, int questionCount) {}
}