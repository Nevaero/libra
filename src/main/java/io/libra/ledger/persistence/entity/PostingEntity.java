package io.libra.ledger.persistence.entity;

import io.libra.core.persistence.entity.MoneyEntity;
import io.libra.ledger.domain.enums.PostingType;
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

import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "postings")
public class PostingEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "sequence_in_entry", nullable = false)
    private long sequenceInEntry;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "amount_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "amount_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "amount_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "amount_asset_mic", length = 4))
    })
    private MoneyEntity amount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "minorUnits", column = @Column(name = "balance_after_minor_units", nullable = false)),
            @AttributeOverride(name = "assetType", column = @Column(name = "balance_after_asset_type", nullable = false, length = 10)),
            @AttributeOverride(name = "assetCode", column = @Column(name = "balance_after_asset_code", nullable = false, length = 20)),
            @AttributeOverride(name = "assetMic", column = @Column(name = "balance_after_asset_mic", length = 4))
    })
    private MoneyEntity balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PostingType type;
}
