package io.libra.customer.domain;

import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    @Test
    void closedStatusRequiresClosedAt() {
        assertThatThrownBy(() -> customer(CustomerStatus.CLOSED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt");
    }

    @Test
    void nonClosedStatusForbidsClosedAt() {
        assertThatThrownBy(() -> customer(CustomerStatus.ACTIVE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt");
    }

    @Test
    void countryOfResidenceMustBeTwoChars() {
        assertThatThrownBy(() -> new Customer(Uuids.newId(), "a@b.io", "A", "B",
                LocalDate.of(1990, 1, 1), "CHE", CustomerStatus.ACTIVE, KycLevel.BASIC,
                RiskProfile.BALANCED, ClientCategory.RETAIL, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("countryOfResidence");
    }

    @Test
    void validCustomerConstructs() {
        assertThatCode(() -> customer(CustomerStatus.ACTIVE, null)).doesNotThrowAnyException();
    }

    private Customer customer(CustomerStatus status, Instant closedAt) {
        return new Customer(Uuids.newId(), "a@b.io", "A", "B", LocalDate.of(1990, 1, 1),
                "CH", status, KycLevel.BASIC, RiskProfile.BALANCED, ClientCategory.RETAIL,
                Instant.now(), closedAt);
    }
}
