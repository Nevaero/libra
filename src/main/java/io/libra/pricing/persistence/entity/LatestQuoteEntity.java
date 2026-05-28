package io.libra.pricing.persistence.entity;

import io.libra.pricing.entities.enums.Tenor;
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
@Table(name = "latest_quotes")
public class LatestQuoteEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Tenor tenor;

    @Column(name = "bid_minor_units", nullable = false)
    private long bidMinorUnits;

    @Column(name = "ask_minor_units", nullable = false)
    private long askMinorUnits;

    @Column(name = "bid_size", nullable = false)
    private long bidSize;

    @Column(name = "ask_size", nullable = false)
    private long askSize;

    @Column(name = "price_scale", nullable = false)
    private int priceScale;

    @Column(name = "quote_time", nullable = false)
    private Instant quoteTime;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(nullable = false)
    private long sequence;
}
