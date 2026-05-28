package io.libra;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

// Verifies the Spring Modulith structure : every cross-module access goes through an exposed type
// (base package or @NamedInterface), and declared allowedDependencies are respected. Fails the
// build on any boundary violation.
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(LibraApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }
}
