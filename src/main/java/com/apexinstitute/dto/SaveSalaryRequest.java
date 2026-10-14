package com.apexinstitute.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class SaveSalaryRequest {

    @NotNull
    private Long teacherId;

    @NotNull @Min(1) @Max(12)
    private Integer month;

    @NotNull @Min(2020) @Max(2100)
    private Integer year;

    @NotNull @Min(0)
    private Double amount;

    @NotBlank @Pattern(regexp = "Bank Transfer|Cash|Cheque")
    private String method;

    private boolean markPaid;

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public boolean isMarkPaid() { return markPaid; }
    public void setMarkPaid(boolean markPaid) { this.markPaid = markPaid; }
}