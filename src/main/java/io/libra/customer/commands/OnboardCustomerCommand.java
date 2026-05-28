package io.libra.customer.commands;

import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.RiskProfile;

import java.time.LocalDate;

// Captured at onboarding : civil identity + the regulatory dimensions known then (MIFID
// category + the suitability questionnaire outcome). KYC starts NONE and status PENDING_KYC —
// the service sets those, not the caller.
public record OnboardCustomerCommand(
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String countryOfResidence,
        ClientCategory clientCategory,
        RiskProfile riskProfile
) { }
