package io.libra.pricing.repository;

import io.libra.pricing.domain.enums.Tenor;
import io.libra.pricing.persistence.entity.LatestQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LatestQuoteRepository extends JpaRepository<LatestQuoteEntity, UUID> {

    Optional<LatestQuoteEntity> findByInstrumentIdAndTenor(UUID instrumentId, Tenor tenor);

    // Atomic, lock-free optimistic upsert : insert the quote, or update the existing one only
    // if the incoming sequence is strictly newer. `sequence` is the version column. The DO
    // UPDATE ... WHERE makes stale / equal (replayed) ticks a no-op → returns 0 rows; a fresh
    // insert or a winning update → 1. The caller publishes QuoteAdvanced iff the result is > 0.
    @Modifying
    @Query(value = """
            INSERT INTO latest_quotes (id, instrument_id, tenor, bid_minor_units, ask_minor_units,
                                       bid_size, ask_size, price_scale, quote_time, received_at,
                                       provider_id, sequence, last_price_minor_units, last_size)
            VALUES (:id, :instrumentId, :tenor, :bidMinorUnits, :askMinorUnits,
                    :bidSize, :askSize, :priceScale, :quoteTime, :receivedAt,
                    :providerId, :sequence,
                    CAST(:lastPriceMinorUnits AS bigint), CAST(:lastSize AS bigint))
            ON CONFLICT (instrument_id, tenor) DO UPDATE SET
                bid_minor_units = EXCLUDED.bid_minor_units,
                ask_minor_units = EXCLUDED.ask_minor_units,
                bid_size        = EXCLUDED.bid_size,
                ask_size        = EXCLUDED.ask_size,
                price_scale     = EXCLUDED.price_scale,
                quote_time      = EXCLUDED.quote_time,
                received_at     = EXCLUDED.received_at,
                provider_id     = EXCLUDED.provider_id,
                sequence        = EXCLUDED.sequence,
                -- COALESCE : a quote-only tick (NULL last) keeps the last trade already stored.
                last_price_minor_units = COALESCE(EXCLUDED.last_price_minor_units, latest_quotes.last_price_minor_units),
                last_size              = COALESCE(EXCLUDED.last_size, latest_quotes.last_size)
            WHERE latest_quotes.sequence < EXCLUDED.sequence
            """, nativeQuery = true)
    int upsertIfNewer(@Param("id") UUID id,
                      @Param("instrumentId") UUID instrumentId,
                      @Param("tenor") String tenor,
                      @Param("bidMinorUnits") long bidMinorUnits,
                      @Param("askMinorUnits") long askMinorUnits,
                      @Param("bidSize") long bidSize,
                      @Param("askSize") long askSize,
                      @Param("priceScale") int priceScale,
                      @Param("quoteTime") Instant quoteTime,
                      @Param("receivedAt") Instant receivedAt,
                      @Param("providerId") UUID providerId,
                      @Param("sequence") long sequence,
                      @Param("lastPriceMinorUnits") Long lastPriceMinorUnits,
                      @Param("lastSize") Long lastSize);
}
