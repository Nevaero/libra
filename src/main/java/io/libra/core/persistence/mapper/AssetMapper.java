package io.libra.core.persistence.mapper;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Security;
import io.libra.core.repository.CurrencyRepository;
import io.libra.core.repository.SecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

// Polymorphic Asset mapper. Not a MapStruct interface — a plain Spring component
// referenced via `uses = AssetMapper.class` by mappers that embed an Asset reference.
//
// Domain to persistence: pure pattern matching on the sealed hierarchy → produces
// (assetType, assetCode, assetMic) without any IO. `assetMic` is NULL for Currency,
// populated with the listing venue MIC for Security.
//
// Persistence to domain: requires a repository lookup. For CURRENCY we use the
// 3-letter ISO code as PK; for SECURITY we look up by the tuple (ticker, mic) since
// a ticker alone is NOT unique (delisting/relisting, multi-venue listing).
//
// @Cacheable on toDomain avoids N+1 reads when rehydrating a JournalEntry with
// many postings — Caffeine config in application.properties.
@Component
public class AssetMapper {

    public static final String TYPE_CURRENCY = "CURRENCY";
    public static final String TYPE_SECURITY = "SECURITY";

    private final CurrencyRepository currencyRepository;
    private final SecurityRepository securityRepository;
    private final CurrencyMapper currencyMapper;
    private final SecurityMapper securityMapper;

    @Autowired
    public AssetMapper(CurrencyRepository currencyRepository,
                       SecurityRepository securityRepository,
                       CurrencyMapper currencyMapper,
                       SecurityMapper securityMapper) {
        this.currencyRepository = currencyRepository;
        this.securityRepository = securityRepository;
        this.currencyMapper = currencyMapper;
        this.securityMapper = securityMapper;
    }

    // Domain → persistence projection (assetType discriminator).
    public String typeOf(Asset asset) {
        if (asset == null) {
            return null;
        }
        return switch (asset) {
            case Currency c -> TYPE_CURRENCY;
            case Security s -> TYPE_SECURITY;
        };
    }

    // Domain → persistence projection (assetCode = ISO for Currency, ticker for Security).
    public String codeOf(Asset asset) {
        return asset == null ? null : asset.code();
    }

    // Domain → persistence projection (assetMic = NULL for Currency, MIC for Security).
    public String micOf(Asset asset) {
        if (asset == null) {
            return null;
        }
        return switch (asset) {
            case Currency c -> null;
            case Security s -> s.mic();
        };
    }

    // Persistence → domain rehydration. Cached: same (type, code, mic) triple resolves
    // to the same Asset throughout the cache TTL — avoids the N+1 repository hit when
    // rehydrating a JournalEntry with N postings sharing a few asset references.
    //
    // `condition` filters out the null-input case BEFORE proxy interception evaluates the
    // SpEL key, so we never feed a null-bearing key to Caffeine.
    @Cacheable(
            value = "assets",
            key = "#assetType + ':' + #assetCode + ':' + (#assetMic == null ? '' : #assetMic)",
            condition = "#assetType != null && #assetCode != null"
    )
    public Asset toDomain(String assetType, String assetCode, String assetMic) {
        if (assetType == null || assetCode == null) {
            return null;
        }
        return switch (assetType) {
            case TYPE_CURRENCY -> currencyRepository.findById(assetCode)
                    .map(currencyMapper::toDomain)
                    .orElseThrow(() -> new IllegalStateException(
                            "Currency not found for code: " + assetCode));
            case TYPE_SECURITY -> {
                if (assetMic == null) {
                    throw new IllegalArgumentException(
                            "Security lookup requires a MIC (ticker=" + assetCode + ")");
                }
                yield securityRepository.findByTickerAndMic(assetCode, assetMic)
                        .map(securityMapper::toDomain)
                        .orElseThrow(() -> new IllegalStateException(
                                "Security not found for (ticker=" + assetCode + ", mic=" + assetMic + ")"));
            }
            default -> throw new IllegalArgumentException(
                    "Unknown asset type: " + assetType);
        };
    }
}
