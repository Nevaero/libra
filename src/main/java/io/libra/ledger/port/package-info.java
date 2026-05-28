// Ledger's published service interfaces. Exposed as the `port` named interface; `domain` and
// `commands` are separate named interfaces. internal, persistence, repository, service and
// port.impl stay encapsulated.
@NamedInterface("port")
package io.libra.ledger.port;

import org.springframework.modulith.NamedInterface;
