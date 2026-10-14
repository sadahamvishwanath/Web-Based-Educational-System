package com.apexinstitute.dto;

import java.util.List;

public class ManagementSalariesDto {

    private final Stats stats;
    private final List<TeacherOption> teachers;
    private final List<SalaryItem> salaries;

    public ManagementSalariesDto(Stats stats, List<TeacherOption> teachers,
                                 List<SalaryItem> salaries) {
        this.stats = stats;
        this.teachers = teachers;
        this.salaries = salaries;
    }

    public Stats getStats() { return stats; }
    public List<TeacherOption> getTeachers() { return teachers; }
    public List<SalaryItem> getSalaries() { return salaries; }

    public record Stats(double total, double paid, double pending,
                        int paidCount, int pendingCount) {}

    public record TeacherOption(Long id, String name) {}

    public record SalaryItem(Long id, String teacherName,
                             String month, int year, double amount,
                             String status, String method,
                             String paidOn, String recordedBy) {}
}