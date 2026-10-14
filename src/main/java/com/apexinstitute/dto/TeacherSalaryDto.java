package com.apexinstitute.dto;

import java.util.List;

public class TeacherSalaryDto {

    private final TeacherSummary teacher;
    private final Stats stats;
    private final List<SalaryItem> history;
    private final List<MonthBar> chart;

    public TeacherSalaryDto(TeacherSummary teacher, Stats stats,
                            List<SalaryItem> history, List<MonthBar> chart) {
        this.teacher = teacher;
        this.stats = stats;
        this.history = history;
        this.chart = chart;
    }

    public TeacherSummary getTeacher() { return teacher; }
    public Stats getStats() { return stats; }
    public List<SalaryItem> getHistory() { return history; }
    public List<MonthBar> getChart() { return chart; }

    public record TeacherSummary(Long id, String teacherCode, String fullName) {}

    public record Stats(double ytdTotal, int paidCount,
                        int pendingCount, double pendingAmount,
                        double monthlyRate) {}

    public record SalaryItem(Long id, String month, int year,
                             double amount, String status,
                             String method, String paidOn, String reference) {}

    public record MonthBar(String monthShort, int year, double amount, String status) {}
}