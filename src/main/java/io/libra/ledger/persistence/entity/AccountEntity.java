package io.libra.ledger.persistence.entity;

import io.libra.ledger.domain.enums.account.AccountStatus;
import io.libra.ledger.domain.enums.account.AccountType;
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

// Asset polymorphism is flattened into (asset_type, asset_code) per the project
// convention (see AssetMapper). The mapper resolves the domain Asset on read.
@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "asset_type", nullable = false, length = 10)
    private String assetType;

    @Column(name = "asset_code", nullable = false, length = 20)
    private String assetCode;

    // NULL for CURRENCY accounts ; venue MIC for SECURITY accounts.
    @Column(name = "asset_mic", length = 4)
    private String assetMic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountType type;

    @Column(nullable = false)
    private boolean pending;

    @Column(length = 128)
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
