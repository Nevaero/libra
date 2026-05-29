// Inbound REST adapter. Thin controllers, one per module, map request/response DTOs to and from
// each module's facade port. No security yet. It depends only on the published ports, domain, and
// command interfaces of the modules it exposes, never on their internals.
@ApplicationModule(
        displayName = "API",
        allowedDependencies = {
                "core", "util",
                "customer :: port", "customer :: domain", "customer :: commands",
                "reference :: port", "reference :: commands",
                "pricing :: port", "pricing :: domain",
                "ledger :: port", "ledger :: domain",
                "trading :: port", "trading :: domain", "trading :: commands"
        }
)
package io.libra.api;

import org.springframework.modulith.ApplicationModule;
