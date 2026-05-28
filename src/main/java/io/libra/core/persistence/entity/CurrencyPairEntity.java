package io.libra.core.persistence.entity;

import io.libra.core.entities.enums.CurrencyPairStatus;
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

import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "currency_pairs")
public class CurrencyPairEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "base_currency_code", nullable = false, length = 3)
    private String baseCurrencyCode;

    @Column(name = "quote_currency_code", nullable = false, length = 3)
    private String quoteCurrencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurrencyPairStatus status;

    @Column(name = "price_scale", nullable = false)
    private int priceScale;
}
