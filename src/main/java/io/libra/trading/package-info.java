// The order orchestrator and last module: it drives the synchronous command path, calling down
// into validation (pre-trade gate), pricing (execution price), ledger (DvP booking) and settlement
// (T+2 scheduling). Dependencies point strictly downward, so nothing depends on trading.
@ApplicationModule(
        displayName = "Trading",
        allowedDependencies = {
                "core", "util",
                "ledger :: port", "ledger :: domain", "ledger :: commands",
                "pricing :: port", "pricing :: domain",
                "validation :: port", "validation :: domain",
                "settlement :: port", "settlement :: domain"
        }
)
package io.libra.trading;

import org.springframework.modulith.ApplicationModule;
