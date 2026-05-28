// Reference Data / Security Master : owns the instrument + currency referential (persistence,
// lifecycle) and provides the AssetResolver/InstrumentResolver implementations for the
// resolution SPI declared in core. A closed module — consumers depend on core's abstractions,
// never on this module's internals.
@ApplicationModule(
        displayName = "Reference Data",
        allowedDependencies = {"core"}
)
package io.libra.reference;

import org.springframework.modulith.ApplicationModule;