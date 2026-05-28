package io.libra.reference.internal;

import io.libra.core.entities.CurrencyPair;
import io.libra.core.entities.Instrument;
import io.libra.core.entities.Security;
import io.libra.core.entities.enums.CurrencyPairStatus;
import io.libra.core.entities.enums.SecurityStatus;
import io.libra.core.persistence.resolution.InstrumentRef;
import io.libra.reference.commands.RegisterCurrencyPairCommand;
import io.libra.reference.commands.RegisterSecurityCommand;
import io.libra.reference.events.InstrumentListed;
import io.libra.reference.events.InstrumentStatusChanged;
import io.libra.reference.persistence.entity.CurrencyPairEntity;
import io.libra.reference.persistence.entity.InstrumentEntity;
import io.libra.reference.persistence.entity.SecurityEntity;
import io.libra.reference.persistence.mapper.CurrencyPairMapper;
import io.libra.reference.persistence.mapper.SecurityMapper;
import io.libra.reference.port.ReferenceDataService;
import io.libra.reference.repository.CurrencyPairRepository;
import io.libra.reference.repository.InstrumentRepository;
import io.libra.reference.repository.SecurityRepository;
import io.libra.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private final InstrumentRepository instrumentRepository;

    private final SecurityRepository securityRepository;

    private final CurrencyPairRepository currencyPairRepository;

    private final SecurityMapper securityMapper;

    private final CurrencyPairMapper currencyPairMapper;

    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------------
    // Registration — insert the instruments(id, type) parent row first, then the child.
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public Security registerSecurity(RegisterSecurityCommand cmd) {
        UUID id = Uuids.newId();
        instrumentRepository.save(new InstrumentEntity(id, InstrumentRef.SECURITY));

        Instant listedAt = Instant.now();
        SecurityEntity entity = new SecurityEntity(
                id, cmd.isin(), cmd.ticker(), cmd.mic(), cmd.quoteCurrencyCode(),
                cmd.name(), cmd.type(), SecurityStatus.ACTIVE, listedAt, null);
        securityRepository.save(entity);

        events.publishEvent(new InstrumentListed(id, listedAt, cmd.providerId()));
        return securityMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public CurrencyPair registerCurrencyPair(RegisterCurrencyPairCommand cmd) {
        UUID id = Uuids.newId();
        instrumentRepository.save(new InstrumentEntity(id, InstrumentRef.CURRENCY_PAIR));

        CurrencyPairEntity entity = new CurrencyPairEntity(
                id, cmd.baseCurrencyCode(), cmd.quoteCurrencyCode(),
                CurrencyPairStatus.ACTIVE, cmd.priceScale());
        currencyPairRepository.save(entity);

        events.publishEvent(new InstrumentListed(id, Instant.now(), cmd.providerId()));
        return currencyPairMapper.toDomain(entity);
    }

    // ---------------------------------------------------------------------
    // Security lifecycle
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public Security activateSecurity(UUID id) {
        return transitionSecurity(id, SecurityStatus.ACTIVE,
                EnumSet.of(SecurityStatus.PENDING_LISTING, SecurityStatus.SUSPENDED, SecurityStatus.HALTED),
                "activated", false);
    }

    @Override
    @Transactional
    public Security suspendSecurity(UUID id, String reason) {
        return transitionSecurity(id, SecurityStatus.SUSPENDED,
                EnumSet.of(SecurityStatus.ACTIVE), reason, false);
    }

    @Override
    @Transactional
    public Security haltSecurity(UUID id, String reason) {
        return transitionSecurity(id, SecurityStatus.HALTED,
                EnumSet.of(SecurityStatus.ACTIVE), reason, false);
    }

    @Override
    @Transactional
    public Security delistSecurity(UUID id, String reason) {
        return transitionSecurity(id, SecurityStatus.DELISTED,
                EnumSet.of(SecurityStatus.PENDING_LISTING, SecurityStatus.ACTIVE,
                        SecurityStatus.SUSPENDED, SecurityStatus.HALTED),
                reason, true);
    }

    private Security transitionSecurity(UUID id, SecurityStatus target,
                                        Set<SecurityStatus> allowedFrom, String reason, boolean terminal) {
        SecurityEntity entity = securityRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Security not found: " + id));
        SecurityStatus current = entity.getStatus();
        if (!allowedFrom.contains(current)) {
            throw new IllegalStateException(
                    "Cannot transition security " + id + " from " + current + " to " + target
                            + " (allowed from " + allowedFrom + ")");
        }
        entity.setStatus(target);
        if (terminal) {
            entity.setDelistedAt(Instant.now());
        }
        securityRepository.save(entity);
        events.publishEvent(new InstrumentStatusChanged(id, current, target, reason, Instant.now()));
        return securityMapper.toDomain(entity);
    }

    // ---------------------------------------------------------------------
    // CurrencyPair lifecycle
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public CurrencyPair activatePair(UUID id) {
        return transitionPair(id, CurrencyPairStatus.ACTIVE,
                EnumSet.of(CurrencyPairStatus.SUSPENDED), "activated");
    }

    @Override
    @Transactional
    public CurrencyPair suspendPair(UUID id, String reason) {
        return transitionPair(id, CurrencyPairStatus.SUSPENDED,
                EnumSet.of(CurrencyPairStatus.ACTIVE), reason);
    }

    @Override
    @Transactional
    public CurrencyPair deactivatePair(UUID id, String reason) {
        return transitionPair(id, CurrencyPairStatus.DEACTIVATED,
                EnumSet.of(CurrencyPairStatus.ACTIVE, CurrencyPairStatus.SUSPENDED), reason);
    }

    private CurrencyPair transitionPair(UUID id, CurrencyPairStatus target,
                                        Set<CurrencyPairStatus> allowedFrom, String reason) {
        CurrencyPairEntity entity = currencyPairRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("CurrencyPair not found: " + id));
        CurrencyPairStatus current = entity.getStatus();
        if (!allowedFrom.contains(current)) {
            throw new IllegalStateException(
                    "Cannot transition currency pair " + id + " from " + current + " to " + target
                            + " (allowed from " + allowedFrom + ")");
        }
        entity.setStatus(target);
        currencyPairRepository.save(entity);
        events.publishEvent(new InstrumentStatusChanged(id, current, target, reason, Instant.now()));
        return currencyPairMapper.toDomain(entity);
    }

    // ---------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<Instrument> findInstrument(UUID id) {
        return instrumentRepository.findById(id).map(meta -> switch (meta.getInstrumentType()) {
            case InstrumentRef.SECURITY -> (Instrument) securityMapper.toDomain(
                    securityRepository.findById(id).orElseThrow());
            case InstrumentRef.CURRENCY_PAIR -> (Instrument) currencyPairMapper.toDomain(
                    currencyPairRepository.findById(id).orElseThrow());
            default -> throw new IllegalStateException(
                    "Unknown instrument type: " + meta.getInstrumentType());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Security> findSecurity(UUID id) {
        return securityRepository.findById(id).map(securityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrencyPair> findCurrencyPair(UUID id) {
        return currencyPairRepository.findById(id).map(currencyPairMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Instrument> listActiveInstruments() {
        List<Instrument> active = new ArrayList<>();
        securityRepository.findByStatus(SecurityStatus.ACTIVE)
                .forEach(e -> active.add(securityMapper.toDomain(e)));
        currencyPairRepository.findByStatus(CurrencyPairStatus.ACTIVE)
                .forEach(e -> active.add(currencyPairMapper.toDomain(e)));
        return active;
    }
}
