package com.apexinstitute.dto;

import java.util.List;

public class ParentInvoicesDto {

    private final ParentSummary parent;
    private final Stats stats;
    private final List<InvoiceItem> invoices;

    public ParentInvoicesDto(ParentSummary parent, Stats stats,
                             List<InvoiceItem> invoices) {
        this.parent = parent;
        this.stats = stats;
        this.invoices = invoices;
    }

    public ParentSummary getParent() { return parent; }
    public Stats getStats() { return stats; }
    public List<InvoiceItem> getInvoices() { return invoices; }

    /* ---------- nested records ---------- */

    public record ParentSummary(Long id, String nic, String fullName) {}

    public record Stats(double total, double paid, double unpaid,
                        int paidCount, int unpaidCount) {}

    public record InvoiceItem(Long id, String invoiceNo,
                              String childName, Long childId,
                              String issueDate, String dueDate,
                              double total, String status,
                              List<LineItem> items) {}

    public record LineItem(String description, double amount) {}
}