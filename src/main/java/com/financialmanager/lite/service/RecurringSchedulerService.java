package com.financialmanager.lite.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringSchedulerService {

    private final EntryService entryService;

    @Scheduled(cron = "0 0 6 * * *")
    public void applyDueRecurring() {
        int count = entryService.applyDueRecurringEntries();
        if (count > 0) {
            log.info("Scheduler: {} recorrência(s) lançada(s) automaticamente.", count);
        }
    }
}
