package io.libra;

import io.libra.reference.commands.RegisterCurrencyPairCommand;
import io.libra.reference.port.ReferenceDataService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(TestDataProperties.class)
public class TestcontainersConfiguration {

    // Seeded "Libra Simulator" provider (V1__schema.sql).
    private static final UUID LIBRA_SIM = UUID.fromString("0190a000-0001-7000-8000-000000000001");

    // FX price scale used for every test pair (5 decimals, matching the seeded majors).
    private static final int TEST_PRICE_SCALE = 5;

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native:latest"));
    }

    // Same major version as prod (compose.yaml) so Flyway runs against the real PG18 behaviour.
    // @ServiceConnection overrides the datasource in application.properties — tests are hermetic
    // and never touch the local compose database.
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));
    }

    // Registers the FX pairs declared under libra.test.currency-pairs once at startup, idempotently
    // (find-or-register). Tests then resolve pairs via ReferenceDataService rather than registering
    // them, which collided on the shared container's unique (base, quote) constraint.
    @Bean
    ApplicationRunner testCurrencyPairSeeder(ReferenceDataService referenceData, TestDataProperties properties) {
        return args -> {
            if (properties.currencyPairs() == null) {
                return;
            }
            properties.currencyPairs().forEach(pair -> {
                if (referenceData.findPairByCodes(pair.base(), pair.quote()).isEmpty()) {
                    referenceData.registerCurrencyPair(new RegisterCurrencyPairCommand(
                            pair.base(), pair.quote(), TEST_PRICE_SCALE, LIBRA_SIM));
                }
            });
        };
    }

}
