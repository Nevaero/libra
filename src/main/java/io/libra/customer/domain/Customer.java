package io.libra.customer.domain;

import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

// Domain record. Invariants enforced in the compact constructor.
// Persistence handled by CustomerEntity + CustomerMapper.
public record Customer(
        UUID id,
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        // ISO 3166-1 alpha-2, e.g. "CH", "FR", "US".
        String countryOfResidence,
        CustomerStatus status,
        KycLevel kycLevel,
        // MIFID II suitability test outcome — input to pre-trade validation.
        RiskProfile riskProfile,
        // MIFID II categorization — drives regulatory protection level.
        ClientCategory clientCategory,
        Instant onboardedAt,
        // Non-null iff status == CLOSED.
        Instant closedAt
) {

    public Customer {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(birthDate, "birthDate must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(kycLevel, "kycLevel must not be null");
        Objects.requireNonNull(riskProfile, "riskProfile must not be null");
        Objects.requireNonNull(clientCategory, "clientCategory must not be null");
        Objects.requireNonNull(onboardedAt, "onboardedAt must not be null");
        if (countryOfResidence == null || countryOfResidence.length() != 2) {
            throw new IllegalArgumentException(
                    "countryOfResidence must be ISO 3166-1 alpha-2 (2 chars), got: " + countryOfResidence);
        }
        if (status == CustomerStatus.CLOSED && closedAt == null) {
            throw new IllegalArgumentException("closedAt must be set when status is CLOSED");
        }
        if (status != CustomerStatus.CLOSED && closedAt != null) {
            throw new IllegalArgumentException("closedAt must be null unless status is CLOSED");
        }
    }
}
