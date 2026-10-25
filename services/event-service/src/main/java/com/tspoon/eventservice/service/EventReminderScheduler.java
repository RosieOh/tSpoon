package com.tspoon.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tspoon.event.reminder", name = "enabled", matchIfMissing = true)
public class EventReminderScheduler {

    private final EventReminderService reminderService;

    @Scheduled(fixedDelayString = "${tspoon.event.reminder.interval:30s}", initialDelayString = "${tspoon.event.reminder.interval:30s}")
    public void sendDueReminders() {
        reminderService.sendDueReminders();
    }
}
