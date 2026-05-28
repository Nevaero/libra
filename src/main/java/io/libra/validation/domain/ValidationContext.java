package io.libra.validation.domain;

import io.libra.customer.domain.Customer;
import io.libra.ledger.domain.Balance;
import io.libra.pricing.domain.LatestQuote;

import java.util.Optional;

// Snapshot enrichi des entités utiles aux règles, construit par le service de validation
// avant d'itérer sur la chaîne de règles. Évite des fetch répétés depuis chaque règle.
public record ValidationContext(
        ValidationRequest request,
        Customer customer,
        Balance sourceBalance,
        // vide si market closed ou si pricing n'a pas de quote récent pour cet instrument.
        Optional<LatestQuote> latestQuote
) {
}
