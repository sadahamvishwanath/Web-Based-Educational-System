package com.apexinstitute.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class SaveFeeRequest {

    @NotBlank @Pattern(regexp = "10|11|12|13")
    private String grade;

    @NotBlank
    private String feeType;

    @NotNull @Min(0)
    private Double amount;

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getFeeType() { return feeType; }
    public void setFeeType(String feeType) { this.feeType = feeType; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}