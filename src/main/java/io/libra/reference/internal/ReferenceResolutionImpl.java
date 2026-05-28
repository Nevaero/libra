package io.libra.reference.internal;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Instrument;
import io.libra.core.persistence.resolution.AssetRef;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.core.persistence.resolution.InstrumentRef;
import io.libra.core.persistence.resolution.InstrumentResolver;
import io.libra.core.persistence.resolution.ReferenceResolution;
import io.libra.reference.persistence.mapper.CurrencyMapper;
import io.libra.reference.persistence.mapper.CurrencyPairMapper;
import io.libra.reference.persistence.mapper.SecurityMapper;
import io.libra.reference.repository.CurrencyPairRepository;
import io.libra.reference.repository.CurrencyRepository;
import io.libra.reference.repository.SecurityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Eager, map-backed implementation. The DB reads happen inside the factory call (within its
// transaction) ; the returned resolver is a pure in-memory lookup, safe to invoke later from
// a mapper that runs outside any transaction.
@Component
@RequiredArgsConstructor
public class ReferenceResolutionImpl implements ReferenceResolution {

    private final CurrencyRepository currencyRepository;

    private final SecurityRepository securityRepository;

    private final CurrencyPairRepository currencyPairRepository;

    private final CurrencyMapper currencyMapper;

    private final SecurityMapper securityMapper;

    private final CurrencyPairMapper currencyPairMapper;

    @Override
    @Transactional(readOnly = true)
    public AssetResolver assetResolverFor(Collection<AssetRef> refs) {
        Map<AssetRef, Asset> resolved = new HashMap<>();

        List<String> currencyCodes = refs.stream()
                .filter(r -> AssetRef.CURRENCY.equals(r.type()))
                .map(AssetRef::code)
                .distinct()
                .toList();
        if (!currencyCodes.isEmpty()) {
            currencyRepository.findAllById(currencyCodes).forEach(e ->
                    resolved.put(new AssetRef(AssetRef.CURRENCY, e.getCode(), null),
                            currencyMapper.toDomain(e)));
        }

        List<String> tickers = refs.stream()
                .filter(r -> AssetRef.SECURITY.equals(r.type()))
                .map(AssetRef::code)
                .distinct()
                .toList();
        if (!tickers.isEmpty()) {
            securityRepository.findByTickerIn(tickers).forEach(e ->
                    resolved.put(new AssetRef(AssetRef.SECURITY, e.getTicker(), e.getMic()),
                            securityMapper.toDomain(e)));
        }

        return ref -> {
            Asset asset = resolved.get(ref);
            if (asset == null) {
                throw new IllegalStateException("Unresolved asset reference: " + ref);
            }
            return asset;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public InstrumentResolver instrumentResolverFor(Collection<InstrumentRef> refs) {
        Map<InstrumentRef, Instrument> resolved = new HashMap<>();

        List<UUID> securityIds = refs.stream()
                .filter(r -> InstrumentRef.SECURITY.equals(r.type()))
                .map(InstrumentRef::id)
                .distinct()
                .toList();
        if (!securityIds.isEmpty()) {
            securityRepository.findAllById(securityIds).forEach(e ->
                    resolved.put(new InstrumentRef(InstrumentRef.SECURITY, e.getId()),
                            securityMapper.toDomain(e)));
        }

        List<UUID> pairIds = refs.stream()
                .filter(r -> InstrumentRef.CURRENCY_PAIR.equals(r.type()))
                .map(InstrumentRef::id)
                .distinct()
                .toList();
        if (!pairIds.isEmpty()) {
            currencyPairRepository.findAllById(pairIds).forEach(e ->
                    resolved.put(new InstrumentRef(InstrumentRef.CURRENCY_PAIR, e.getId()),
                            currencyPairMapper.toDomain(e)));
        }

        return ref -> {
            Instrument instrument = resolved.get(ref);
            if (instrument == null) {
                throw new IllegalStateException("Unresolved instrument reference: " + ref);
            }
            return instrument;
        };
    }
}
