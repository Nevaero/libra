package io.libra.settlement.internal;

import io.libra.settlement.port.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

// The morning batch trigger : settles every instruction that has reached its value date. The
// settlement logic lives in SettlementService.runDueBatch — this is just the cron wrapper.
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;

    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "UTC")
    public void runDailyBatch() {
        settlementService.runDueBatch(LocalDate.now(ZoneOffset.UTC));
    }
}
