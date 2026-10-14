package com.apexinstitute.dto;

import jakarta.validation.constraints.NotBlank;

public class PayInvoiceRequest {

    @NotBlank
    private String method;   // "Card" | "Bank" | "Wallet"

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
}