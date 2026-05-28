package io.libra.ledger.persistence.entity;

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
@EqualsAndHashCode(of = "accountId")
@ToString(of = "accountId")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "balances")
public class BalanceEntity {

    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "asset_type", nullable = false, length = 10)
    private String assetType;

    @Column(name = "asset_code", nullable = false, length = 20)
    private String assetCode;

    // NULL for CURRENCY balances ; venue MIC for SECURITY balances.
    @Column(name = "asset_mic", length = 4)
    private String assetMic;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "book_balance_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "book_balance_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "book_balance_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "book_balance_asset_mic", length = 4))
    })
    private MoneyEntity bookBalance;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "available_balance_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "available_balance_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "available_balance_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "available_balance_asset_mic", length = 4))
    })
    private MoneyEntity availableBalance;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "pending_debits_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "pending_debits_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "pending_debits_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "pending_debits_asset_mic", length = 4))
    })
    private MoneyEntity pendingDebits;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "pending_credits_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "pending_credits_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "pending_credits_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "pending_credits_asset_mic", length = 4))
    })
    private MoneyEntity pendingCredits;

    @Column(name = "last_posting_id")
    private UUID lastPostingId;

    @Column(name = "last_posting_sequence_number", nullable = false)
    private long lastPostingSequenceNumber;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
