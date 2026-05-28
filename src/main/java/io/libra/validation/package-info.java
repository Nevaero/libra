@ApplicationModule(
        displayName = "Validation",
        allowedDependencies = {
                "core",
                "ledger :: port", "ledger :: domain",
                "pricing :: port", "pricing :: domain",
                "customer :: port", "customer :: domain"
        }
)
package io.libra.validation;

import org.springframework.modulith.ApplicationModule;
