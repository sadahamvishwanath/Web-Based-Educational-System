package com.apexinstitute.dto;

import java.util.List;

public class AdminRoomsDto {

    private final List<RoomItem> rooms;
    private final int total;
    private final int inUse;

    public AdminRoomsDto(List<RoomItem> rooms, int total, int inUse) {
        this.rooms = rooms;
        this.total = total;
        this.inUse = inUse;
    }

    public List<RoomItem> getRooms() { return rooms; }
    public int getTotal() { return total; }
    public int getInUse() { return inUse; }

    public record RoomItem(Long id, String name, int usageCount) {}
}