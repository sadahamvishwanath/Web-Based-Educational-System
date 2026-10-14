package com.apexinstitute.controller;

import com.apexinstitute.dto.*;
import com.apexinstitute.service.ParentChildrenService;
import com.apexinstitute.service.ParentDashboardService;
import com.apexinstitute.service.ParentInvoicesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/parent")
public class ParentController {

    private final ParentDashboardService dashboard;
    private final ParentChildrenService children;
    private final ParentInvoicesService invoices;

    public ParentController(ParentDashboardService dashboard,
                            ParentChildrenService children,
                            ParentInvoicesService invoices) {
        this.dashboard = dashboard;
        this.children = children;
        this.invoices = invoices;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('PARENT')")
    public ParentDashboardDto dashboard() {
        return dashboard.load();
    }

    @GetMapping("/children")
    @PreAuthorize("hasRole('PARENT')")
    public ParentChildrenDto children() {
        return children.list();
    }

    @GetMapping("/child/{id}")
    @PreAuthorize("hasRole('PARENT')")
    public ParentChildDetailDto child(@PathVariable Long id) {
        return children.detail(id);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('PARENT')")
    public ParentInvoicesDto invoices() {
        return invoices.list();
    }

    @PostMapping("/invoices/{id}/pay")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> pay(@PathVariable Long id,
                                 @Valid @RequestBody PayInvoiceRequest req) {
        try {
            String ref = invoices.pay(id, req);
            return ResponseEntity.ok(Map.of(
                    "message", "Payment successful",
                    "reference", ref));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }
}