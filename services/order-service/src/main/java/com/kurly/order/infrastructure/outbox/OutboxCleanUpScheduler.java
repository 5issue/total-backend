package com.kurly.order.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanUpScheduler {

    private final CompletedEventPublications completedEvents;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanUpOldCompletedEvents() {
        log.info("Starting cleanup for completed outbox events...");
        completedEvents.deletePublicationsOlderThan(Duration.ofDays(7));
        log.info("Finished outbox cleanup.");
    }
}
