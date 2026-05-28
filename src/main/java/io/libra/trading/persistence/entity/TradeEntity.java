package io.libra.trading.persistence.entity;

import io.libra.core.persistence.entity.MoneyEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
@Table(name = "trades")
public class TradeEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "counterparty_id", nullable = false)
    private UUID counterpartyId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "executed_quantity_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "executed_quantity_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "executed_quantity_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "executed_quantity_asset_mic", length = 4))
    })
    private MoneyEntity executedQuantity;

    @Column(name = "executed_price_minor_units", nullable = false)
    private long executedPriceMinorUnits;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;
}
