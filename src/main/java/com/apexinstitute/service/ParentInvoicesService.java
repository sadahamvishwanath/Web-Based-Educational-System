package com.apexinstitute.service;

import com.apexinstitute.dto.ParentInvoicesDto;
import com.apexinstitute.dto.ParentInvoicesDto.InvoiceItem;
import com.apexinstitute.dto.ParentInvoicesDto.LineItem;
import com.apexinstitute.dto.ParentInvoicesDto.Stats;
import com.apexinstitute.dto.PayInvoiceRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ParentInvoicesService {

    private final JdbcTemplate jdbc;
    private final ParentContext ctx;

    public ParentInvoicesService(JdbcTemplate jdbc, ParentContext ctx) {
        this.jdbc = jdbc;
        this.ctx = ctx;
    }

    /* ---------- GET /api/parent/invoices ---------- */
    public ParentInvoicesDto list() {
        ParentContext.ParentInfo p = ctx.current();

        List<InvoiceItem> invoices = jdbc.query("""
                SELECT i.id, i.invoice_no, i.issue_date, i.due_date,
                       i.total_amount, i.status,
                       s.id AS child_id, u.full_name AS child_name
                FROM invoices i
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                JOIN parent_child pc ON pc.student_id = s.id
                WHERE pc.parent_id = ?
                ORDER BY i.status DESC, i.due_date DESC
                """,
                (rs, i) -> {
                    Long invoiceId = rs.getLong("id");
                    List<LineItem> items = jdbc.query("""
                            SELECT description, amount FROM invoice_items
                            WHERE invoice_id = ? ORDER BY id
                            """,
                            (rs2, i2) -> new LineItem(
                                    rs2.getString("description"),
                                    rs2.getBigDecimal("amount").doubleValue()),
                            invoiceId);

                    return new InvoiceItem(
                            invoiceId,
                            rs.getString("invoice_no"),
                            rs.getString("child_name"),
                            rs.getLong("child_id"),
                            rs.getDate("issue_date").toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            rs.getDate("due_date").toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            rs.getBigDecimal("total_amount").doubleValue(),
                            rs.getString("status"),
                            items);
                },
                p.id());

        double total = invoices.stream().mapToDouble(InvoiceItem::total).sum();
        double paid  = invoices.stream()
                .filter(x -> "paid".equals(x.status()))
                .mapToDouble(InvoiceItem::total).sum();
        double unpaid = total - paid;
        int paidCount = (int) invoices.stream().filter(x -> "paid".equals(x.status())).count();
        int unpaidCount = invoices.size() - paidCount;

        Stats stats = new Stats(round1(total), round1(paid), round1(unpaid),
                paidCount, unpaidCount);

        ParentInvoicesDto.ParentSummary summary =
                new ParentInvoicesDto.ParentSummary(p.id(), p.nic(), p.fullName());

        return new ParentInvoicesDto(summary, stats, invoices);
    }

    /* ---------- POST /api/parent/invoices/{id}/pay ---------- */
    @Transactional
    public String pay(Long invoiceId, PayInvoiceRequest req) {
        ParentContext.ParentInfo p = ctx.current();

        // Invoice must belong to one of this parent's children, and be unpaid
        InvoiceMeta inv = jdbc.query("""
                SELECT i.id, i.total_amount, i.status
                FROM invoices i
                JOIN students s ON s.id = i.student_id
                JOIN parent_child pc ON pc.student_id = s.id
                WHERE i.id = ? AND pc.parent_id = ?
                """,
                rs -> rs.next()
                        ? new InvoiceMeta(rs.getLong("id"),
                        rs.getBigDecimal("total_amount").doubleValue(),
                        rs.getString("status"))
                        : null,
                invoiceId, p.id());

        if (inv == null)
            throw new RuntimeException("Invoice not found or not yours");
        if ("paid".equals(inv.status()))
            throw new RuntimeException("Invoice is already paid");

        // Record the payment
        String reference = "ONL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // recorded_by = the parent's user id
        Long parentUserId = jdbc.queryForObject(
                "SELECT user_id FROM parents WHERE id = ?", Long.class, p.id());

        jdbc.update("""
                INSERT INTO payments
                    (invoice_id, amount, payment_date, method, reference_no,
                     recorded_by, created_at)
                VALUES (?, ?, CAST(SYSDATETIME() AS DATE), 'Online', ?, ?, SYSDATETIME())
                """,
                invoiceId, inv.totalAmount(), reference, parentUserId);

        // Mark invoice paid
        jdbc.update("UPDATE invoices SET status = 'paid' WHERE id = ?", invoiceId);

        return reference;
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }

    private record InvoiceMeta(Long id, double totalAmount, String status) {}
}