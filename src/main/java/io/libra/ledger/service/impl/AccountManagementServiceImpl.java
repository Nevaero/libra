package io.libra.ledger.service.impl;

import io.libra.core.entities.Asset;
import io.libra.core.entities.Currency;
import io.libra.core.entities.Money;
import io.libra.core.entities.Security;
import io.libra.core.persistence.resolution.AssetRefs;
import io.libra.core.persistence.resolution.AssetResolver;
import io.libra.core.persistence.resolution.ReferenceResolution;
import io.libra.ledger.commands.OpenAccountCommand;
import io.libra.ledger.domain.Account;
import io.libra.ledger.domain.Balance;
import io.libra.ledger.domain.enums.account.AccountStatus;
import io.libra.ledger.domain.enums.account.AccountType;
import io.libra.ledger.events.AccountOpened;
import io.libra.ledger.events.AccountStatusChanged;
import io.libra.ledger.persistence.LedgerRefs;
import io.libra.ledger.persistence.entity.AccountEntity;
import io.libra.ledger.persistence.mapper.AccountMapper;
import io.libra.ledger.persistence.mapper.BalanceMapper;
import io.libra.ledger.repository.AccountRepository;
import io.libra.ledger.repository.BalanceRepository;
import io.libra.ledger.service.AccountManagementService;
import io.libra.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountManagementServiceImpl implements AccountManagementService {

    // Fixed system identity owning every house counterparty account (the other side of client
    // trades). One owner ; the AccountType (FX_COUNTERPARTY / MARKET_COUNTERPARTY) discriminates.
    private static final UUID HOUSE_COUNTERPARTY_OWNER =
            UUID.fromString("0190a000-0000-7000-8000-0000000000ff");

    private final AccountRepository accountRepository;

    private final AccountMapper accountMapper;

    private final BalanceRepository balanceRepository;

    private final BalanceMapper balanceMapper;

    private final ReferenceResolution referenceResolution;

    private final ApplicationEventPublisher events;

    @Override
    public Optional<Account> findAccountById(UUID id) {
        return accountRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Account> findAccountsByOwnerId(UUID ownerId) {
        List<AccountEntity> entities = accountRepository.findAccountsByOwnerId(ownerId);
        AssetResolver resolver = referenceResolution.assetResolverFor(
                entities.stream().map(LedgerRefs::of).toList());
        return entities.stream().map(e -> accountMapper.toDomain(e, resolver)).toList();
    }

    // Single-account rehydration : one batch resolution of the (single) asset it holds.
    private Account toDomain(AccountEntity entity) {
        AssetResolver resolver = referenceResolution.assetResolverFor(List.of(LedgerRefs.of(entity)));
        return accountMapper.toDomain(entity, resolver);
    }

    @Override
    @Transactional                                    // ← TX englobante
    public Account openAccount(OpenAccountCommand cmd) {
        Instant now = Instant.now();
        Account account = new Account(
                Uuids.newId(),                            // UUIDv7 — convention projet
                cmd.ownerId(),
                cmd.asset(),
                AccountStatus.OPEN,
                cmd.type(),
                cmd.pending(),
                cmd.label(),
                now,
                null
        );

        AccountEntity entity = accountMapper.toEntity(account);
        accountRepository.save(entity);

        // Invariant : 1 Account ↔ 1 Balance row, créée dans la même TX que l'Account.
        // Évite tout fallback lazy côté PostingService.
        Money zero = new Money(0L, account.asset());
        Balance balance = new Balance(
                account.id(),
                account.asset(),
                zero,    // bookBalance
                zero,    // availableBalance
                zero,    // pendingDebits
                zero,    // pendingCredits
                null,    // lastPostingId
                0L,      // lastPostingSequenceNumber
                now
        );
        balanceRepository.save(balanceMapper.toEntity(balance));

        // Publication de l'event — Spring Modulith outbox l'intercepte
        events.publishEvent(new AccountOpened(
                account.id(), account.type(), account.pending(), account.ownerId(), account.createdAt()
        ));

        return account;
    }

    @Override
    @Transactional
    public Account freezeAccount(UUID id, String reason) {
        return transition(id, AccountStatus.FROZEN, EnumSet.of(AccountStatus.OPEN), reason, false);
    }

    @Override
    @Transactional
    public Account unfreezeAccount(UUID id, String reason) {
        return transition(id, AccountStatus.OPEN, EnumSet.of(AccountStatus.FROZEN), reason, false);
    }

    @Override
    public Optional<Account> findMirrorAccount(UUID accountId) {
        return accountRepository.findById(accountId).flatMap(source ->
                accountRepository.findByOwnerIdAndAssetTypeAndAssetCodeAndAssetMicAndTypeAndPending(
                        source.getOwnerId(),
                        source.getAssetType(),
                        source.getAssetCode(),
                        source.getAssetMic(),
                        source.getType(),
                        !source.isPending()
                ).map(this::toDomain));
    }

    @Override
    public Optional<Account> findClientAccount(UUID ownerId, Asset asset) {
        AccountType type = clientAccountTypeFor(asset);
        return accountRepository.findByOwnerIdAndAssetTypeAndAssetCodeAndAssetMicAndTypeAndPending(
                        ownerId, AssetRefs.typeOf(asset), AssetRefs.codeOf(asset),
                        AssetRefs.micOf(asset), type, false)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public Account resolveClientAccount(UUID ownerId, Asset asset, boolean pending) {
        return resolveOrOpen(ownerId, clientAccountTypeFor(asset), asset, pending);
    }

    @Override
    @Transactional
    public Account resolveCounterpartyAccount(Asset asset, boolean pending) {
        return resolveOrOpen(HOUSE_COUNTERPARTY_OWNER, counterpartyAccountTypeFor(asset), asset, pending);
    }

    private Account resolveOrOpen(UUID ownerId, AccountType type, Asset asset, boolean pending) {
        return accountRepository.findByOwnerIdAndAssetTypeAndAssetCodeAndAssetMicAndTypeAndPending(
                        ownerId, AssetRefs.typeOf(asset), AssetRefs.codeOf(asset),
                        AssetRefs.micOf(asset), type, pending)
                .map(this::toDomain)
                .orElseGet(() -> openAccount(new OpenAccountCommand(
                        ownerId, type, asset, pending,
                        type + ":" + asset.code() + (pending ? ":pending" : ":final"))));
    }

    // A currency is held in cash, a security as a position. Sealed switch — no default.
    private AccountType clientAccountTypeFor(Asset asset) {
        return switch (asset) {
            case Currency _ -> AccountType.CLIENT_CASH;
            case Security _ -> AccountType.CLIENT_POSITION;
        };
    }

    // FX delivers/receives currency against the FX desk ; equity trades face the market venue.
    private AccountType counterpartyAccountTypeFor(Asset asset) {
        return switch (asset) {
            case Currency _ -> AccountType.FX_COUNTERPARTY;
            case Security _ -> AccountType.MARKET_COUNTERPARTY;
        };
    }

    @Override
    @Transactional
    public Account closeAccount(UUID id, String reason) {
        return transition(id, AccountStatus.CLOSED,
                EnumSet.of(AccountStatus.OPEN, AccountStatus.FROZEN, AccountStatus.PENDING_ACTIVATION),
                reason, true);
    }

    /**
     * Helper unique pour les transitions de status :
     * - vérifie que le compte existe
     * - vérifie que le status courant autorise la transition
     * - construit une nouvelle instance d'Account (records immuables)
     * - persiste et publie AccountStatusChanged dans la même TX (outbox)
     */
    private Account transition(UUID id, AccountStatus targetStatus, Set<AccountStatus> allowedFrom,
                                String reason, boolean setClosedAt) {
        Account current = findAccountById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + id));

        if (!allowedFrom.contains(current.status())) {
            throw new IllegalStateException(
                    "Cannot transition account " + id + " from " + current.status()
                            + " to " + targetStatus + " (allowed from " + allowedFrom + ")"
            );
        }

        Account transitioned = new Account(
                current.id(),
                current.ownerId(),
                current.asset(),
                targetStatus,
                current.type(),
                current.pending(),
                current.label(),
                current.createdAt(),
                setClosedAt ? Instant.now() : current.closedAt()
        );

        AccountEntity entity = accountMapper.toEntity(transitioned);
        accountRepository.save(entity);

        events.publishEvent(new AccountStatusChanged(
                transitioned.id(), current.status(), targetStatus, reason
        ));

        return transitioned;
    }
}
