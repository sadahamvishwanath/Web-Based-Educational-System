package com.apexinstitute.service;

import com.apexinstitute.dto.ManagementPaymentsDto;
import com.apexinstitute.dto.ManagementPaymentsDto.PaymentItem;
import com.apexinstitute.dto.ManagementPaymentsDto.Stats;
import com.apexinstitute.dto.ManagementPaymentsDto.UnpaidInvoiceOption;
import com.apexinstitute.dto.RecordPaymentRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ManagementPaymentsService {

    private final JdbcTemplate jdbc;

    public ManagementPaymentsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- GET /api/management/payments ---------- */
    public ManagementPaymentsDto list() {

        List<PaymentItem> payments = jdbc.query("""
                SELECT p.id, p.amount, p.payment_date, p.method,
                       p.reference_no,
                       i.invoice_no,
                       u.full_name AS student_name,
                       r.full_name AS recorded_by
                FROM payments p
                JOIN invoices i ON i.id = p.invoice_id
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                LEFT JOIN users r ON r.id = p.recorded_by
                ORDER BY p.created_at DESC
                """,
                (rs, i) -> new PaymentItem(
                        rs.getLong("id"),
                        rs.getString("invoice_no"),
                        rs.getString("student_name"),
                        rs.getBigDecimal("amount").doubleValue(),
                        rs.getDate("payment_date").toLocalDate()
                                .format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                        rs.getString("method"),
                        rs.getString("reference_no"),
                        rs.getString("recorded_by")));

        List<UnpaidInvoiceOption> unpaid = jdbc.query("""
                SELECT i.id, i.invoice_no, i.total_amount,
                       u.full_name AS student_name, s.student_id
                FROM invoices i
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                WHERE i.status = 'unpaid'
                ORDER BY i.due_date
                """,
                (rs, i) -> new UnpaidInvoiceOption(
                        rs.getLong("id"),
                        rs.getString("invoice_no"),
                        rs.getString("student_name"),
                        rs.getString("student_id"),
                        rs.getBigDecimal("total_amount").doubleValue()));

        double total = payments.stream().mapToDouble(PaymentItem::amount).sum();
        double cash = payments.stream().filter(x -> "Cash".equals(x.method()))
                .mapToDouble(PaymentItem::amount).sum();
        double digital = total - cash;
        int cashCount = (int) payments.stream().filter(x -> "Cash".equals(x.method())).count();

        Stats stats = new Stats(round1(total), round1(cash), round1(digital),
                cashCount, payments.size() - cashCount);

        return new ManagementPaymentsDto(stats, unpaid, payments);
    }

    /* ---------- POST /api/management/payments ---------- */
    @Transactional
    public void record(RecordPaymentRequest req) {

        // Invoice must exist and be unpaid
        Double invoiceTotal = jdbc.query("""
                SELECT total_amount FROM invoices WHERE id = ?
                """, rs -> rs.next() ? rs.getBigDecimal("total_amount").doubleValue() : null,
                req.getInvoiceId());
        if (invoiceTotal == null)
            throw new RuntimeException("Invoice not found");

        String status = jdbc.query("""
                SELECT status FROM invoices WHERE id = ?
                """, rs -> rs.next() ? rs.getString("status") : null,
                req.getInvoiceId());
        if ("paid".equals(status))
            throw new RuntimeException("Invoice is already paid");

        if (req.getAmount() > invoiceTotal + 0.001)
            throw new RuntimeException("Amount exceeds invoice total of Rs. " + invoiceTotal);

        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        String reference = req.getReference() != null && !req.getReference().isBlank()
                ? req.getReference().trim()
                : ("MAN-" + System.currentTimeMillis() % 100000);

        jdbc.update("""
                INSERT INTO payments
                    (invoice_id, amount, payment_date, method, reference_no,
                     recorded_by, created_at)
                VALUES (?, ?, CAST(SYSDATETIME() AS DATE), ?, ?, ?, SYSDATETIME())
                """,
                req.getInvoiceId(), req.getAmount(), req.getMethod(),
                reference, userId);

        // If fully covered, mark the invoice paid
        if (req.getAmount() >= invoiceTotal - 0.001) {
            jdbc.update("UPDATE invoices SET status = 'paid' WHERE id = ?",
                    req.getInvoiceId());
        }
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }
}