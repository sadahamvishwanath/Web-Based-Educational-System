package com.apexinstitute.dto;

import java.util.List;

public class StudentAvailableExamsDto {

    private final List<AvailableExam> exams;

    public StudentAvailableExamsDto(List<AvailableExam> exams) {
        this.exams = exams;
    }

    public List<AvailableExam> getExams() { return exams; }

    public record AvailableExam(
            Long id,
            String title,
            String className,
            String subject,
            String teacher,
            int questionCount,
            int totalMarks) {}
}