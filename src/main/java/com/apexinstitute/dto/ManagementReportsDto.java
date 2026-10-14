package com.apexinstitute.dto;

import java.util.List;

public class ManagementReportsDto {

    private final IncomeReport income;
    private final List<GradeIncome> incomeByGrade;
    private final List<GradeAttendance> attendance;
    private final List<Defaulter> defaulters;

    public ManagementReportsDto(IncomeReport income,
                                List<GradeIncome> incomeByGrade,
                                List<GradeAttendance> attendance,
                                List<Defaulter> defaulters) {
        this.income = income;
        this.incomeByGrade = incomeByGrade;
        this.attendance = attendance;
        this.defaulters = defaulters;
    }

    public IncomeReport getIncome() { return income; }
    public List<GradeIncome> getIncomeByGrade() { return incomeByGrade; }
    public List<GradeAttendance> getAttendance() { return attendance; }
    public List<Defaulter> getDefaulters() { return defaulters; }

    public record IncomeReport(double monthTotal, double ytdTotal,
                               List<MethodBreakdown> byMethod) {}

    public record MethodBreakdown(String method, double amount) {}

    public record GradeIncome(String grade, int students, double income) {}

    public record GradeAttendance(String grade, double pct) {}

    public record Defaulter(String studentName, String studentCode,
                            String grade, double amount, String since) {}
}