// Synchronous orchestration model : trading calls settlement.scheduleSettlement(...) directly
// (passing the booking entry id), so settlement does NOT depend on trading — only on ledger
// (postSettlementEntry at T+2) and core. Dependencies point downward, no cycle.
@ApplicationModule(
        displayName = "Settlement",
        allowedDependencies = {"core", "util", "ledger :: port"}
)
package io.libra.settlement;

import org.springframework.modulith.ApplicationModule;
