// The order orchestrator and last module : it drives the synchronous command path, calling down
// into validation (pre-trade gate), pricing (execution price), ledger (DvP booking) and settlement
// (T+2 scheduling). Dependencies point strictly downward — nothing depends on trading.
@ApplicationModule(
        displayName = "Trading",
        allowedDependencies = {"core", "ledger", "pricing", "validation", "settlement", "ledger :: port"}
)
package io.libra.trading;

import org.springframework.modulith.ApplicationModule;
