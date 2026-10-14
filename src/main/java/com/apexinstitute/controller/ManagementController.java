package com.apexinstitute.controller;

import com.apexinstitute.dto.*;
import com.apexinstitute.service.ManagementDashboardService;
import com.apexinstitute.service.ManagementFeesService;
import com.apexinstitute.service.ManagementSalariesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.apexinstitute.service.ManagementInvoicesService;
import com.apexinstitute.service.ManagementPaymentsService;
import com.apexinstitute.service.ManagementReportsService;

import java.util.Map;

@RestController
@RequestMapping("/api/management")
public class ManagementController {

    private final ManagementDashboardService dashboard;
    private final ManagementFeesService fees;
    private final ManagementSalariesService salaries;
    private final ManagementInvoicesService invoices;
    private final ManagementPaymentsService payments;
    private final ManagementReportsService reports;

    // In the constructor params:
    public ManagementController(ManagementDashboardService dashboard,
                                ManagementFeesService fees,
                                ManagementSalariesService salaries,
                                ManagementInvoicesService invoices,
                                ManagementPaymentsService payments,
                                ManagementReportsService reports) {
        this.dashboard = dashboard;
        this.fees = fees;
        this.salaries = salaries;
        this.invoices = invoices;
        this.payments = payments;
        this.reports = reports;
    }

    /* ---------- Dashboard ---------- */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ManagementDashboardDto dashboard() { return dashboard.load(); }

    /* ---------- Fees ---------- */
    @GetMapping("/fees")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ManagementFeesDto fees() { return fees.list(); }

    @PostMapping("/fees")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> createFee(@Valid @RequestBody SaveFeeRequest req) {
        try {
            Long id = fees.create(req);
            return ResponseEntity.ok(Map.of("message", "Fee added", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PutMapping("/fees/{id}")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> updateFee(@PathVariable Long id,
                                       @Valid @RequestBody SaveFeeRequest req) {
        try {
            fees.update(id, req);
            return ResponseEntity.ok(Map.of("message", "Fee updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/fees/{id}")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> deleteFee(@PathVariable Long id) {
        try {
            fees.delete(id);
            return ResponseEntity.ok(Map.of("message", "Fee deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Salaries ---------- */
    @GetMapping("/salaries")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ManagementSalariesDto salaries() { return salaries.list(); }

    @PostMapping("/salaries")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> createSalary(@Valid @RequestBody SaveSalaryRequest req) {
        try {
            Long id = salaries.create(req);
            return ResponseEntity.ok(Map.of("message", "Salary record added", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PatchMapping("/salaries/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> markPaid(@PathVariable Long id) {
        try {
            salaries.markPaid(id);
            return ResponseEntity.ok(Map.of("message", "Salary marked paid"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/salaries/{id}")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> deleteSalary(@PathVariable Long id) {
        try {
            salaries.delete(id);
            return ResponseEntity.ok(Map.of("message", "Salary deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Invoices ---------- */
    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ManagementInvoicesDto invoices() { return invoices.list(); }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> createInvoice(@Valid @RequestBody CreateInvoiceRequest req) {
        try {
            Long id = invoices.create(req);
            return ResponseEntity.ok(Map.of("message", "Invoice created", "id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PatchMapping("/invoices/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> markInvoicePaid(@PathVariable Long id) {
        try {
            invoices.markPaid(id);
            return ResponseEntity.ok(Map.of("message", "Invoice marked paid"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> deleteInvoice(@PathVariable Long id) {
        try {
            invoices.delete(id);
            return ResponseEntity.ok(Map.of("message", "Invoice deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Payments ---------- */
    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ManagementPaymentsDto payments() { return payments.list(); }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ResponseEntity<?> recordPayment(@Valid @RequestBody RecordPaymentRequest req) {
        try {
            payments.record(req);
            return ResponseEntity.ok(Map.of("message", "Payment recorded"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    /* ---------- Reports ---------- */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('MANAGEMENT','ADMIN')")
    public ManagementReportsDto reports() { return reports.load(); }
}