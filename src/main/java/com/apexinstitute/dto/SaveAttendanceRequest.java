package com.apexinstitute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SaveAttendanceRequest {

    @NotNull
    private Long classId;

    @NotBlank
    private String date;   // yyyy-MM-dd

    @NotEmpty
    private List<Entry> entries;

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }

    public static class Entry {
        @NotNull private Long studentId;
        @NotBlank private String status;   // Present | Late | Absent

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}