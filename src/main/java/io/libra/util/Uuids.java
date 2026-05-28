package io.libra.util;

import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.util.UuidUtil;

import java.time.Instant;
import java.util.UUID;

/**
 * Factory state-of-the-art pour les UUID Libra. <strong>Toujours UUIDv7</strong> (RFC 9562, mai 2024)
 * — ordre chronologique préservé, indexes B-tree PostgreSQL optimaux, débit d'insertion ~2x supérieur
 * à UUIDv4 sur les tables très indexées.
 *
 * <h3>Structure d'un UUIDv7</h3>
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           unix_ts_ms                          |  ← 48 bits
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          unix_ts_ms           |  ver  |       rand_a          |  ← 12 bits random + version 7
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var|                        rand_b                             |  ← variant + 62 bits random
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                            rand_b                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <h3>Pourquoi UUIDv7 sur Libra</h3>
 * <ul>
 *   <li><b>Ordre chronologique</b> — les UUIDs générés successivement sont triables. Critique pour
 *       les indexes B-tree de Postgres : un index UUID est ~2x plus rapide à insérer qu'avec UUIDv4
 *       (random) sur des tables > 10M lignes.</li>
 *   <li><b>Embedded timestamp</b> — on peut extraire l'instant de création depuis l'UUID lui-même
 *       (utile pour audit, debugging, partitionnement par date).</li>
 *   <li><b>Standard RFC</b> — interopérable avec d'autres systèmes (Postgres extension uuid-v7,
 *       Kafka, autres microservices) sans wrapper custom.</li>
 *   <li><b>Pas de coordination</b> — chaque pod génère ses UUIDs localement, pas de séquence DB
 *       partagée (vs BIGSERIAL qui demande un lock).</li>
 * </ul>
 *
 * <h3>Implémentation</h3>
 * Délègue à {@code com.github.f4b6a3:uuid-creator} (Fabio Lima) — la lib de référence Java pour
 * UUIDv6/v7/v8, utilisée par Hibernate ORM 7+ pour son générateur natif UUIDv7. ThreadSafe.
 *
 * <h3>Convention projet</h3>
 * <strong>Tout ID UUID dans Libra DOIT être généré via cette classe.</strong> Jamais
 * {@code UUID.randomUUID()} (v4 — random, désordonné, indexes lents).
 *
 * <pre>{@code
 * UUID accountId = Uuids.newId();
 * Instant createdAt = Uuids.extractTimestamp(accountId);
 * }</pre>
 */
public final class Uuids {

    private Uuids() {
        // utility — pas d'instance
    }

    /**
     * Génère un UUID v7 — ordre chronologique préservé, idéal pour PK et indexes.
     * Source de référence : RFC 9562, Section 5.7.
     *
     * @return un UUID v7 unique
     */
    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * Extrait le timestamp embarqué dans un UUID v7. Si l'UUID n'est pas v7, le résultat est
     * indéfini (sera probablement absurde).
     *
     * @param uuid un UUID préalablement généré via {@link #newId()}
     * @return l'instant de création approximatif (précision milliseconde)
     */
    public static Instant extractTimestamp(UUID uuid) {
        return UuidUtil.getInstant(uuid);
    }
}
