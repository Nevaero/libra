package io.libra.core.persistence.entity;

import io.libra.core.entities.enums.SecurityStatus;
import io.libra.core.entities.enums.SecurityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "securities")
public class SecurityEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 12, unique = true)
    private String isin;

    @Column(nullable = false, length = 16)
    private String ticker;

    @Column(nullable = false, length = 4)
    private String mic;

    // FK to currencies(code), resolved by the mapper via CurrencyRepository.
    @Column(name = "quote_currency_code", nullable = false, length = 3)
    private String quoteCurrencyCode;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SecurityType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityStatus status;

    @Column(name = "listed_at", nullable = false)
    private Instant listedAt;

    @Column(name = "delisted_at")
    private Instant delistedAt;
}
