// Module utils — VOs / factories transverses accessibles à tous les modules.
// OPEN au sens Spring Modulith : pas de restriction d'accès depuis customer, ledger, trading, etc.
@ApplicationModule(
        displayName = "Utils",
        type = ApplicationModule.Type.OPEN
)
package io.libra.util;

import org.springframework.modulith.ApplicationModule;
