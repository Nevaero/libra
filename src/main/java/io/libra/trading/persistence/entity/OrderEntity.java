package io.libra.trading.persistence.entity;

import io.libra.core.entities.enums.Side;
import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.trading.domain.enums.OrderStatus;
import io.libra.trading.domain.enums.OrderType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "instrument_type", nullable = false, length = 20)
    private String instrumentType;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Side side;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "quantity_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "quantity_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "quantity_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "quantity_asset_mic", length = 4))
    })
    private MoneyEntity quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 8)
    private OrderType orderType;

    @Column(name = "limit_price_minor_units")
    private Long limitPriceMinorUnits;

    @Column(name = "parent_order_id")
    private UUID parentOrderId;
}
