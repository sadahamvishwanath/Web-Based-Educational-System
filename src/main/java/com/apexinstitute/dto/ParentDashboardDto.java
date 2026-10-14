package com.apexinstitute.dto;

import java.util.List;

public class ParentDashboardDto {

    private final ParentSummary parent;
    private final Stats stats;
    private final List<ChildCard> children;
    private final List<UnpaidInvoice> unpaidInvoices;
    private final List<AlertItem> alerts;

    public ParentDashboardDto(ParentSummary parent, Stats stats,
                              List<ChildCard> children,
                              List<UnpaidInvoice> unpaidInvoices,
                              List<AlertItem> alerts) {
        this.parent = parent;
        this.stats = stats;
        this.children = children;
        this.unpaidInvoices = unpaidInvoices;
        this.alerts = alerts;
    }

    public ParentSummary getParent() { return parent; }
    public Stats getStats() { return stats; }
    public List<ChildCard> getChildren() { return children; }
    public List<UnpaidInvoice> getUnpaidInvoices() { return unpaidInvoices; }
    public List<AlertItem> getAlerts() { return alerts; }

    public record ParentSummary(Long id, String nic, String fullName) {}
    public record Stats(int children, double avgAttendance,
                        int unpaidCount, double unpaidTotal,
                        int totalAlerts) {}

    public record ChildCard(Long id, String studentCode, String fullName, String grade,
                            double attendance, int classes, String latestGrade,
                            double latestPct) {}

    public record UnpaidInvoice(Long id, String invoiceNo, String childName,
                                double amount, String due) {}

    public record AlertItem(String title, String text, String time, String kind) {}
}