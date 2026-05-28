package io.libra.settlement.entities.enums;

public enum BatchStatus {
    RUNNING,            // batch en cours d'exécution
    COMPLETED,          // toutes les instructions ont été settled
    PARTIAL_FAILURE,    // certaines instructions ont échoué (alerting requis)
    FAILED              // le batch lui-même a échoué avant la fin (rare — alerting critique)
}
