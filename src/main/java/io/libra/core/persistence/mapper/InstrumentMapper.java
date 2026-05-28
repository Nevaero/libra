package io.libra.core.persistence.mapper;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;
import io.libra.core.repository.CurrencyPairRepository;
import io.libra.core.repository.SecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Polymorphic Instrument mapper. Plain Spring component used by mappers that embed
// an Instrument reference flat as (instrumentType, instrumentId).
//
// @Cacheable on toDomain avoids repeated repository lookups when many Orders / Trades
// reference the same instrument — Caffeine config in application.properties.
@Component
public class InstrumentMapper {

    public static final String TYPE_SECURITY = "SECURITY";
    public static final String TYPE_CURRENCY_PAIR = "CURRENCY_PAIR";

    private final SecurityRepository securityRepository;
    private final CurrencyPairRepository currencyPairRepository;
    private final SecurityMapper securityMapper;
    private final CurrencyPairMapper currencyPairMapper;

    @Autowired
    public InstrumentMapper(SecurityRepository securityRepository,
                            CurrencyPairRepository currencyPairRepository,
                            SecurityMapper securityMapper,
                            CurrencyPairMapper currencyPairMapper) {
        this.securityRepository = securityRepository;
        this.currencyPairRepository = currencyPairRepository;
        this.securityMapper = securityMapper;
        this.currencyPairMapper = currencyPairMapper;
    }

    public String typeOf(Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        return switch (instrument) {
            case Security s -> TYPE_SECURITY;
            case CurrencyPair cp -> TYPE_CURRENCY_PAIR;
        };
    }

    public UUID idOf(Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        return switch (instrument) {
            case Security s -> s.id();
            case CurrencyPair cp -> cp.id();
        };
    }

    @Cacheable(
            value = "instruments",
            key = "#instrumentType + ':' + #instrumentId",
            condition = "#instrumentType != null && #instrumentId != null"
    )
    public Instrument toDomain(String instrumentType, UUID instrumentId) {
        if (instrumentType == null || instrumentId == null) {
            return null;
        }
        return switch (instrumentType) {
            case TYPE_SECURITY -> securityRepository.findById(instrumentId)
                    .map(securityMapper::toDomain)
                    .orElseThrow(() -> new IllegalStateException(
                            "Security not found for id: " + instrumentId));
            case TYPE_CURRENCY_PAIR -> currencyPairRepository.findById(instrumentId)
                    .map(currencyPairMapper::toDomain)
                    .orElseThrow(() -> new IllegalStateException(
                            "CurrencyPair not found for id: " + instrumentId));
            default -> throw new IllegalArgumentException(
                    "Unknown instrument type: " + instrumentType);
        };
    }
}
