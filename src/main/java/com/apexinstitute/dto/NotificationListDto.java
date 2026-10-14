package com.apexinstitute.dto;

import java.util.List;

public class NotificationListDto {

    private final int total;
    private final int unread;
    private final List<Item> notifications;

    public NotificationListDto(int total, int unread, List<Item> notifications) {
        this.total = total;
        this.unread = unread;
        this.notifications = notifications;
    }

    public int getTotal() { return total; }
    public int getUnread() { return unread; }
    public List<Item> getNotifications() { return notifications; }

    public record Item(Long id,
                       String title,
                       String message,
                       String audience,
                       String author,
                       String when,
                       String date,
                       boolean read) {}
}