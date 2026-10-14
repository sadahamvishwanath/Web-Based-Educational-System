package com.apexinstitute.dto;

import java.util.List;

public class ManagementDashboardDto {

    private final Stats stats;
    private final List<MonthBar> monthly;
    private final List<TransactionItem> recent;

    public ManagementDashboardDto(Stats stats,
                                  List<MonthBar> monthly,
                                  List<TransactionItem> recent) {
        this.stats = stats;
        this.monthly = monthly;
        this.recent = recent;
    }

    public Stats getStats() { return stats; }
    public List<MonthBar> getMonthly() { return monthly; }
    public List<TransactionItem> getRecent() { return recent; }

    public record Stats(double monthIncome, double monthExpense,
                        double net, double outstanding,
                        int outstandingCount) {}

    public record MonthBar(String month, int year,
                           double income, double expense) {}

    public record TransactionItem(String kind,   // "income" | "expense"
                                  String title, String meta,
                                  double amount, String when) {}
}