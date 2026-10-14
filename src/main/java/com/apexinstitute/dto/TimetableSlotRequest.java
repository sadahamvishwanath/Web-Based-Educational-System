package com.apexinstitute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TimetableSlotRequest {

    @NotNull private Long classId;
    @NotNull private Long teacherId;
    @NotNull private Long roomId;

    @NotBlank private String day;
    @NotBlank private String start;
    @NotBlank private String end;

    /** Populated for PUT (edit); null for POST (create). */
    private Long excludeId;

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public Long getExcludeId() { return excludeId; }
    public void setExcludeId(Long excludeId) { this.excludeId = excludeId; }
}