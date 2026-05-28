package io.libra.customer.port;

import io.libra.customer.commands.OnboardCustomerCommand;
import io.libra.customer.domain.Customer;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;

import java.util.Optional;
import java.util.UUID;

// Module port for customer : onboarding, the regulatory lifecycle state machine, and reads.
// Every mutation publishes its lifecycle event via the outbox (consumed by validation, audit…).
//
// Status state machine :
//   PENDING_KYC → ACTIVE (KYC done) | CLOSED
//   ACTIVE      → SUSPENDED | CLOSED
//   SUSPENDED   → ACTIVE | CLOSED
//   CLOSED      = terminal
// Activation is gated : a customer cannot go ACTIVE while kycLevel == NONE.
public interface CustomerService {

    Customer onboard(OnboardCustomerCommand command);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByEmail(String email);

    // PENDING_KYC → ACTIVE. Requires kycLevel != NONE.
    Customer activate(UUID id);

    // ACTIVE → SUSPENDED (compliance hold on the whole client, vs a targeted Account freeze).
    Customer suspend(UUID id, String reason);

    // SUSPENDED → ACTIVE.
    Customer reactivate(UUID id);

    // any non-terminal → CLOSED (sets closedAt). Terminal.
    Customer close(UUID id, String reason);

    Customer updateKycLevel(UUID id, KycLevel newLevel);

    Customer updateRiskProfile(UUID id, RiskProfile newProfile);
}
