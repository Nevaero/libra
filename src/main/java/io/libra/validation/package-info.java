@ApplicationModule(
        displayName = "Validation",
        allowedDependencies = {"core", "ledger :: api", "pricing :: api", "customer :: api"}
)
package io.libra.validation;

import org.springframework.modulith.ApplicationModule;
