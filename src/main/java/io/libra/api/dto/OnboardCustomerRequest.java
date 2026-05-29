package io.libra.api.dto;

import io.libra.customer.commands.OnboardCustomerCommand;
import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.RiskProfile;

import java.time.LocalDate;

public record OnboardCustomerRequest(
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String countryOfResidence,
        ClientCategory clientCategory,
        RiskProfile riskProfile
) {

    public OnboardCustomerCommand toCommand() {
        return new OnboardCustomerCommand(
                email, firstName, lastName, birthDate, countryOfResidence, clientCategory, riskProfile);
    }
}
