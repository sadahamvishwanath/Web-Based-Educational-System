package com.apexinstitute.dto;

import java.util.List;

public class AdminNotificationsDto {

    private final Stats stats;
    private final List<NotificationItem> notifications;

    public AdminNotificationsDto(Stats stats, List<NotificationItem> notifications) {
        this.stats = stats;
        this.notifications = notifications;
    }

    public Stats getStats() { return stats; }
    public List<NotificationItem> getNotifications() { return notifications; }

    public record Stats(int total, int general, int students, int parents,
                        int teachers, int classScoped) {}

    public record NotificationItem(Long id, String title, String message,
                                   String audience, String classLabel,
                                   String author, String when, String time,
                                   int recipientCount) {}
}