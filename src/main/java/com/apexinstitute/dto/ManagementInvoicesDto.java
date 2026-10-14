package com.apexinstitute.dto;

import java.util.List;

public class ManagementInvoicesDto {

    private final Stats stats;
    private final List<StudentOption> students;
    private final List<InvoiceItem> invoices;

    public ManagementInvoicesDto(Stats stats, List<StudentOption> students,
                                 List<InvoiceItem> invoices) {
        this.stats = stats;
        this.students = students;
        this.invoices = invoices;
    }

    public Stats getStats() { return stats; }
    public List<StudentOption> getStudents() { return students; }
    public List<InvoiceItem> getInvoices() { return invoices; }

    public record Stats(double total, double paid, double unpaid,
                        int paidCount, int unpaidCount) {}

    public record StudentOption(Long id, String name, String studentCode, String grade) {}

    public record InvoiceItem(Long id, String invoiceNo,
                              Long studentId, String studentName,
                              String issueDate, String dueDate,
                              double total, String status,
                              List<LineItem> items) {}

    public record LineItem(String description, double amount) {}
}