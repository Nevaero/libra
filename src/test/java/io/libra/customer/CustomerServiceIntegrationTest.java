package io.libra.customer;

import io.libra.TestcontainersConfiguration;
import io.libra.customer.commands.OnboardCustomerCommand;
import io.libra.customer.domain.Customer;
import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;
import io.libra.customer.port.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// End-to-end customer lifecycle against a real PostgreSQL 18 : onboarding, the regulatory
// state machine (KYC-gated activation, suspend/reactivate, close) and KYC/risk updates.
// Each test uses a fresh random email so the shared container stays isolated.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CustomerServiceIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Test
    void onboardingStartsPendingKycWithNoKyc() {
        Customer customer = customerService.onboard(onboardCmd());

        assertThat(customer.status()).isEqualTo(CustomerStatus.PENDING_KYC);
        assertThat(customer.kycLevel()).isEqualTo(KycLevel.NONE);
        assertThat(customerService.findById(customer.id())).isPresent();
        assertThat(customerService.findByEmail(customer.email()))
                .get().extracting(Customer::id).isEqualTo(customer.id());
    }

    @Test
    void activationIsGatedOnKyc() {
        UUID id = customerService.onboard(onboardCmd()).id();

        assertThatThrownBy(() -> customerService.activate(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KYC");
    }

    @Test
    void customerWalksTheFullLifecycle() {
        UUID id = customerService.onboard(onboardCmd()).id();

        customerService.updateKycLevel(id, KycLevel.BASIC);
        assertThat(customerService.activate(id).status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customerService.suspend(id, "compliance review").status())
                .isEqualTo(CustomerStatus.SUSPENDED);
        assertThat(customerService.reactivate(id).status()).isEqualTo(CustomerStatus.ACTIVE);

        Customer closed = customerService.close(id, "client left");
        assertThat(closed.status()).isEqualTo(CustomerStatus.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    void illegalTransitionIsRejected() {
        UUID id = customerService.onboard(onboardCmd()).id();
        customerService.close(id, "onboarding rejected");   // PENDING_KYC → CLOSED is legal

        // CLOSED is terminal.
        assertThatThrownBy(() -> customerService.suspend(id, "too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void duplicateEmailIsRejected() {
        OnboardCustomerCommand cmd = onboardCmd();
        customerService.onboard(cmd);

        assertThatThrownBy(() -> customerService.onboard(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void kycAndRiskProfileUpdatesArePersisted() {
        UUID id = customerService.onboard(onboardCmd()).id();

        assertThat(customerService.updateKycLevel(id, KycLevel.ENHANCED).kycLevel())
                .isEqualTo(KycLevel.ENHANCED);
        assertThat(customerService.updateRiskProfile(id, RiskProfile.AGGRESSIVE).riskProfile())
                .isEqualTo(RiskProfile.AGGRESSIVE);
    }

    private OnboardCustomerCommand onboardCmd() {
        String email = "user-" + UUID.randomUUID() + "@libra.io";
        return new OnboardCustomerCommand(email, "Jane", "Doe", LocalDate.of(1990, 1, 1),
                "CH", ClientCategory.RETAIL, RiskProfile.BALANCED);
    }
}
