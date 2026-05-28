package io.libra.core.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// @Embeddable persistence projection of the Money domain record. Flattens the Asset
// reference into (assetType, assetCode, assetMic) so the embedding entity can store Money
// in four plain columns. Column names are remapped via @AttributeOverrides on the
// owning entity.
//
// `assetMic` is nullable: it's only populated when assetType=SECURITY, since the security
// business identity is the tuple (ticker, mic). For CURRENCY it stays NULL.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class MoneyEntity {

    private long minorUnits;

    // CURRENCY | SECURITY
    private String assetType;

    // ISO code for currency, ticker for security.
    private String assetCode;

    // MIC of the listing venue. NULL for currency, NOT NULL for security.
    private String assetMic;
}
