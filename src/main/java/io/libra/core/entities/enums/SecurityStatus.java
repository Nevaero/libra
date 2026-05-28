package io.libra.core.entities.enums;

public enum SecurityStatus implements InstrumentStatus {
    PENDING_LISTING,    // annoncée, pas encore échangeable (avant IPO)
    ACTIVE,             // échangeable normalement
    SUSPENDED,          // temporairement arrêtée
    HALTED,             // arrêt intra-day par l'exchange (volatilité, news...)
    DELISTED            // définitivement retirée du marché
}
