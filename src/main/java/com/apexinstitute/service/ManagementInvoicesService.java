package com.apexinstitute.service;

import com.apexinstitute.dto.CreateInvoiceRequest;
import com.apexinstitute.dto.ManagementInvoicesDto;
import com.apexinstitute.dto.ManagementInvoicesDto.InvoiceItem;
import com.apexinstitute.dto.ManagementInvoicesDto.LineItem;
import com.apexinstitute.dto.ManagementInvoicesDto.Stats;
import com.apexinstitute.dto.ManagementInvoicesDto.StudentOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ManagementInvoicesService {

    private final JdbcTemplate jdbc;

    public ManagementInvoicesService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- GET /api/management/invoices ---------- */
    public ManagementInvoicesDto list() {

        List<InvoiceItem> invoices = jdbc.query("""
                SELECT i.id, i.invoice_no, i.issue_date, i.due_date,
                       i.total_amount, i.status,
                       s.id AS student_id, u.full_name AS student_name
                FROM invoices i
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                ORDER BY i.status DESC, i.due_date DESC, i.id DESC
                """,
                (rs, i) -> {
                    Long invId = rs.getLong("id");
                    List<LineItem> items = jdbc.query("""
                            SELECT description, amount FROM invoice_items
                            WHERE invoice_id = ? ORDER BY id
                            """,
                            (rs2, i2) -> new LineItem(
                                    rs2.getString("description"),
                                    rs2.getBigDecimal("amount").doubleValue()),
                            invId);

                    DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM yyyy");
                    return new InvoiceItem(
                            invId,
                            rs.getString("invoice_no"),
                            rs.getLong("student_id"),
                            rs.getString("student_name"),
                            rs.getDate("issue_date").toLocalDate().format(f),
                            rs.getDate("due_date").toLocalDate().format(f),
                            rs.getBigDecimal("total_amount").doubleValue(),
                            rs.getString("status"),
                            items);
                });

        List<StudentOption> students = jdbc.query("""
                SELECT s.id, s.student_id, s.grade, u.full_name
                FROM students s
                JOIN users u ON u.id = s.user_id
                WHERE s.is_active = 1
                ORDER BY u.full_name
                """,
                (rs, i) -> new StudentOption(
                        rs.getLong("id"),
                        rs.getString("full_name"),
                        rs.getString("student_id"),
                        rs.getString("grade")));

        double total = invoices.stream().mapToDouble(InvoiceItem::total).sum();
        double paid  = invoices.stream()
                .filter(x -> "paid".equals(x.status()))
                .mapToDouble(InvoiceItem::total).sum();
        int paidCount = (int) invoices.stream().filter(x -> "paid".equals(x.status())).count();

        Stats stats = new Stats(round1(total), round1(paid), round1(total - paid),
                paidCount, invoices.size() - paidCount);

        return new ManagementInvoicesDto(stats, students, invoices);
    }

    /* ---------- POST /api/management/invoices ---------- */
    @Transactional
    public Long create(CreateInvoiceRequest req) {

        // Student must exist and be active
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM students WHERE id = ? AND is_active = 1
                """, Integer.class, req.getStudentId());
        if (exists == null || exists == 0)
            throw new RuntimeException("Student not found");

        if (req.getItems().isEmpty())
            throw new RuntimeException("At least one line item is required");

        double total = req.getItems().stream()
                .mapToDouble(CreateInvoiceRequest.LineItem::getAmount).sum();
        if (total <= 0)
            throw new RuntimeException("Invoice total must be greater than zero");

        String invoiceNo = nextInvoiceNo();
        LocalDate dueDate = LocalDate.parse(req.getDueDate());

        // Insert invoice
        jdbc.update("""
                INSERT INTO invoices
                    (invoice_no, student_id, issue_date, due_date,
                     total_amount, status, created_at)
                VALUES (?, ?, CAST(SYSDATETIME() AS DATE), ?, ?, 'unpaid', SYSDATETIME())
                """,
                invoiceNo, req.getStudentId(), Date.valueOf(dueDate), total);

        Long invoiceId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM invoices WHERE invoice_no = ?",
                Long.class, invoiceNo);

        // Insert items
        for (CreateInvoiceRequest.LineItem it : req.getItems()) {
            jdbc.update("""
                    INSERT INTO invoice_items (invoice_id, description, amount)
                    VALUES (?, ?, ?)
                    """,
                    invoiceId, it.getDescription().trim(), it.getAmount());
        }

        return invoiceId;
    }

    /* ---------- PATCH /api/management/invoices/{id}/mark-paid ---------- */
    @Transactional
    public void markPaid(Long id) {
        // Verify it's unpaid
        String status = jdbc.query("""
                SELECT status FROM invoices WHERE id = ?
                """, rs -> rs.next() ? rs.getString("status") : null, id);
        if (status == null) throw new RuntimeException("Invoice not found");
        if ("paid".equals(status)) throw new RuntimeException("Invoice is already paid");

        // Find the manager's user id
        Long userId = Long.parseLong(
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication().getName());

        // Record a manual payment for the full amount
        Double amount = jdbc.queryForObject(
                "SELECT total_amount FROM invoices WHERE id = ?",
                Double.class, id);

        jdbc.update("""
                INSERT INTO payments
                    (invoice_id, amount, payment_date, method, reference_no,
                     recorded_by, created_at)
                VALUES (?, ?, CAST(SYSDATETIME() AS DATE), 'Cash', ?, ?, SYSDATETIME())
                """,
                id, amount, "MAN-" + System.currentTimeMillis() % 100000, userId);

        jdbc.update("UPDATE invoices SET status = 'paid' WHERE id = ?", id);
    }

    /* ---------- DELETE /api/management/invoices/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        String status = jdbc.query("""
                SELECT status FROM invoices WHERE id = ?
                """, rs -> rs.next() ? rs.getString("status") : null, id);
        if (status == null) throw new RuntimeException("Invoice not found");
        if ("paid".equals(status))
            throw new RuntimeException("Cannot delete a paid invoice");

        // Cascade will delete invoice_items
        jdbc.update("DELETE FROM invoices WHERE id = ?", id);
    }

    /* ---------- helpers ---------- */
    private String nextInvoiceNo() {
        Integer maxN = jdbc.queryForObject("""
                SELECT COALESCE(MAX(TRY_CAST(RIGHT(invoice_no, 3) AS INT)), 0)
                FROM invoices
                WHERE invoice_no LIKE 'INV-2026-%'
                """, Integer.class);
        int next = (maxN == null ? 0 : maxN) + 1;
        return "INV-2026-" + String.format("%03d", next);
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }
}