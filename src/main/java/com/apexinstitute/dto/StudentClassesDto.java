package com.apexinstitute.dto;

import java.util.List;

public class StudentClassesDto {

    private final StudentSummary student;
    private final Stats stats;
    private final List<EnrolledClass> enrolled;
    private final List<AvailableClass> available;

    public StudentClassesDto(StudentSummary student,
                             Stats stats,
                             List<EnrolledClass> enrolled,
                             List<AvailableClass> available) {
        this.student = student;
        this.stats = stats;
        this.enrolled = enrolled;
        this.available = available;
    }

    public StudentSummary getStudent() { return student; }
    public Stats getStats() { return stats; }
    public List<EnrolledClass> getEnrolled() { return enrolled; }
    public List<AvailableClass> getAvailable() { return available; }

    public record StudentSummary(Long id, String studentId, String fullName, String grade) {}

    public record Stats(int enrolledCount, int subjectCount,
                        int teacherCount, int availableCount) {}

    public record EnrolledClass(Long id,
                                String name, String subject, String grade,
                                String teacher, String day, String time, String room,
                                String enrolledOn) {}

    public record AvailableClass(Long id,
                                 String name, String subject, String grade,
                                 String teacher, String day, String time, String room) {}
}