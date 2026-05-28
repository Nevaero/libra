package io.libra.reference;

import io.libra.TestcontainersConfiguration;
import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;
import io.libra.core.entities.enums.CurrencyPairStatus;
import io.libra.core.entities.enums.SecurityStatus;
import io.libra.core.entities.enums.SecurityType;
import io.libra.reference.commands.RegisterCurrencyPairCommand;
import io.libra.reference.commands.RegisterSecurityCommand;
import io.libra.reference.port.ReferenceDataService;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// End-to-end Security Master tests : registration (incl. the instruments parent-table insert)
// and the instrument lifecycle state machine, against a real PostgreSQL 18 (Testcontainers).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReferenceDataIntegrationTest {

    @Autowired
    private ReferenceDataService referenceData;

    @Test
    void registeredSecurityGoesLiveAndIsResolvable() {
        Security security = registerSecurity();

        assertThat(security.status()).isEqualTo(SecurityStatus.ACTIVE);
        assertThat(referenceData.findSecurity(security.id()))
                .get().extracting(Security::id).isEqualTo(security.id());
        assertThat(referenceData.findInstrument(security.id())).get().isInstanceOf(Security.class);
    }

    @Test
    void securityWalksTheFullLifecycle() {
        UUID id = registerSecurity().id();

        assertThat(referenceData.suspendSecurity(id, "compliance hold").status())
                .isEqualTo(SecurityStatus.SUSPENDED);
        assertThat(referenceData.activateSecurity(id).status())
                .isEqualTo(SecurityStatus.ACTIVE);
        assertThat(referenceData.haltSecurity(id, "volatility").status())
                .isEqualTo(SecurityStatus.HALTED);
        assertThat(referenceData.activateSecurity(id).status())
                .isEqualTo(SecurityStatus.ACTIVE);

        Security delisted = referenceData.delistSecurity(id, "issuer wind-down");
        assertThat(delisted.status()).isEqualTo(SecurityStatus.DELISTED);
        assertThat(delisted.delistedAt()).isNotNull();
    }

    @Test
    void illegalSecurityTransitionIsRejected() {
        UUID id = registerSecurity().id();
        referenceData.delistSecurity(id, "terminal");

        // DELISTED is terminal — no further transition is legal.
        assertThatThrownBy(() -> referenceData.suspendSecurity(id, "too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELISTED");
    }

    @Test
    void currencyPairWalksItsLifecycle() {
        // GBP/CHF : both currencies are seeded, the pair itself is not.
        CurrencyPair pair = referenceData.registerCurrencyPair(
                new RegisterCurrencyPairCommand("GBP", "CHF", 5, Uuids.newId()));
        assertThat(pair.status()).isEqualTo(CurrencyPairStatus.ACTIVE);

        UUID id = pair.id();
        assertThat(referenceData.suspendPair(id, "venue outage").status())
                .isEqualTo(CurrencyPairStatus.SUSPENDED);
        assertThat(referenceData.activatePair(id).status())
                .isEqualTo(CurrencyPairStatus.ACTIVE);
        assertThat(referenceData.deactivatePair(id, "retired").status())
                .isEqualTo(CurrencyPairStatus.DEACTIVATED);
    }

    @Test
    void listActiveInstrumentsIncludesARegisteredSecurity() {
        UUID id = registerSecurity().id();

        assertThat(referenceData.listActiveInstruments())
                .filteredOn(i -> i instanceof Security s && s.id().equals(id))
                .hasSize(1);
    }

    private Security registerSecurity() {
        String r = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return referenceData.registerSecurity(new RegisterSecurityCommand(
                r.substring(0, 12),      // ISIN (unique)
                "T" + r.substring(0, 8), // ticker (unique within MIC)
                "XTST",                  // MIC
                "CHF",                   // quote currency (seeded)
                "Test Security",
                SecurityType.EQUITY,
                Uuids.newId()));
    }
}
