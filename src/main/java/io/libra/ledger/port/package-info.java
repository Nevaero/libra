// Ledger's published surface (with domain, commands) : the `api` named interface other modules
// may depend on. Everything else (internal, persistence, repository, service, port.impl) is hidden.
@NamedInterface("api")
package io.libra.ledger.port;

import org.springframework.modulith.NamedInterface;
