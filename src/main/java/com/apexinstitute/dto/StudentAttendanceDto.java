package com.apexinstitute.dto;

import java.util.List;

public class StudentAttendanceDto {

    private final StudentSummary student;
    private final Stats stats;
    private final List<SubjectBreakdown> bySubject;
    private final List<LogEntry> recent;

    public StudentAttendanceDto(StudentSummary student,
                                Stats stats,
                                List<SubjectBreakdown> bySubject,
                                List<LogEntry> recent) {
        this.student = student;
        this.stats = stats;
        this.bySubject = bySubject;
        this.recent = recent;
    }

    public StudentSummary getStudent() { return student; }
    public Stats getStats() { return stats; }
    public List<SubjectBreakdown> getBySubject() { return bySubject; }
    public List<LogEntry> getRecent() { return recent; }

    public record StudentSummary(Long id, String studentId, String fullName, String grade) {}

    public record Stats(int total, int present, int absent, int late,
                        double pct, int streak) {}

    public record SubjectBreakdown(String subject,
                                   int total, int present, int absent, int late,
                                   double pct) {}

    public record LogEntry(String date, String day, String mon,
                           String subject, String teacher,
                           String status, String time) {}
}