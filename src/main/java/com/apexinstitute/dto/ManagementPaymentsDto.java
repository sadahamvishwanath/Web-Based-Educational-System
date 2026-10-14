package com.apexinstitute.dto;

import java.util.List;

public class ManagementPaymentsDto {

    private final Stats stats;
    private final List<UnpaidInvoiceOption> unpaidInvoices;
    private final List<PaymentItem> payments;

    public ManagementPaymentsDto(Stats stats,
                                 List<UnpaidInvoiceOption> unpaidInvoices,
                                 List<PaymentItem> payments) {
        this.stats = stats;
        this.unpaidInvoices = unpaidInvoices;
        this.payments = payments;
    }

    public Stats getStats() { return stats; }
    public List<UnpaidInvoiceOption> getUnpaidInvoices() { return unpaidInvoices; }
    public List<PaymentItem> getPayments() { return payments; }

    public record Stats(double total, double cashTotal, double digitalTotal,
                        int cashCount, int digitalCount) {}

    public record UnpaidInvoiceOption(Long id, String invoiceNo,
                                      String studentName, String studentCode,
                                      double total) {}

    public record PaymentItem(Long id, String invoiceNo, String studentName,
                              double amount, String date,
                              String method, String reference,
                              String recordedBy) {}
}