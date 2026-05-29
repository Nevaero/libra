package io.libra.api.dto;

import io.libra.customer.domain.Customer;
import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String countryOfResidence,
        CustomerStatus status,
        KycLevel kycLevel,
        RiskProfile riskProfile,
        ClientCategory clientCategory,
        Instant onboardedAt,
        Instant closedAt
) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.id(), c.email(), c.firstName(), c.lastName(), c.birthDate(), c.countryOfResidence(),
                c.status(), c.kycLevel(), c.riskProfile(), c.clientCategory(), c.onboardedAt(), c.closedAt());
    }
}
