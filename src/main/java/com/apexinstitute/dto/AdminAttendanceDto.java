package com.apexinstitute.dto;

import java.util.List;

public class AdminAttendanceDto {

    private String date;
    private List<Record> records;

    public AdminAttendanceDto(String date, List<Record> records) {
        this.date = date;
        this.records = records;
    }

    public String getDate() { return date; }
    public List<Record> getRecords() { return records; }

    public record Record(
            String studentId,
            String fullName,
            String email,
            String grade,
            String status,
            String source,
            String time) {}
}