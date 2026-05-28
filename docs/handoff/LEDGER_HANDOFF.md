# Libra — Ledger Module Hand-off

> Document de hand-off pour Claude Code (ou tout autre assistant) chargé d'implémenter le module `ledger` de Libra.
> Ce document fixe les décisions d'architecture, le modèle de domaine, et les conventions à respecter.
>
> **Update** : aligné avec la décision **settlement T+2 uniforme** (FX et equity), prise lors de la conception du module `trading`. Le ledger porte désormais la **mécanique two-phase booking via comptes pending dédiés** — voir §4.3bis et §4.4.

---

## 1. Contexte du projet

**Libra** est un broker multi-asset simplifié construit comme side-project pour :
- Démontrer une maîtrise architecturale (DDD, CQRS, event-driven, modular monolith) auprès de Swiss fintech (Swissquote, Lombard Odier, Pictet)
- Implémenter concrètement les concepts de *Designing Data-Intensive Applications* (Kleppmann)
- Modéliser le **Physical Forex** avec settlement T+2 (différenciateur clé vs FX synthétique)

### Stack technique imposée

| Couche | Choix |
|---|---|
| Langage | **Java 21 LTS** (records, pattern matching, sealed interfaces) |
| Framework | **Spring Boot 3.4.x** |
| Architecture | **Modular monolith via Spring Modulith 1.3.x** |
| Persistance | **PostgreSQL 16** + Spring Data JPA + Flyway |
| Messaging | **Spring Kafka** + Kafka (KRaft mode, single-broker local) |
| Build | **Maven** (single-module au début) |
| Tests | JUnit 5, AssertJ, Testcontainers, **jqwik** (property-based), ArchUnit |
| Observabilité | Actuator + Micrometer + Prometheus |
| Doc | springdoc-openapi (Swagger UI) |

### Structure des packages

```
io.libra
├── LibraApplication
├── core                       # value objects partagés (Money, etc.)
├── ledger                     # ← CE MODULE
│   ├── package-info.java      # @ApplicationModule
│   ├── api                    # ports exposés aux autres modules
│   ├── events                 # events publiés
│   └── internal               # impl, hidden
├── trading
├── pricing
├── validation
├── customer
├── settlement
└── api                        # REST controllers + WebSocket
```

---

## 2. Responsabilité du module Ledger

Le module `ledger` est la **source de vérité comptable** de Libra. Il :

1. Tient les **comptes** (cash multi-devises, positions equity, comptes internes Libra, contreparties marché)
2. Enregistre tous les **mouvements** sous forme d'écritures double-entry immuables
3. Maintient une **projection des soldes** pour les lectures rapides
4. Publie des **events** consommés par les autres modules (settlement, projections portfolio, audit)

Il **ne** sait **rien** sur :
- Les ordres (`trading`)
- Les prix de marché (`pricing`)
- Les règles de validation pré-trade (`validation`)
- Les clients (sauf via une référence `customerId` opaque)

---

## 3. Modèle conceptuel

### 3.1 Vocabulaire

| Terme | Définition |
|---|---|
| **Asset** | Ce qui peut être détenu (devise OU titre). Sealed type. |
| **Currency** | Devise fiat (CHF, USD, EUR). Value object pur. |
| **Security** | Titre financier (action, ETF, obligation). Entity avec cycle de vie. |
| **Money** | Montant + asset. Value object immuable. Stocké en minor units (BIGINT). |
| **Account** | Bucket d'un asset pour un owner. Entity. |
| **Posting** | Demi-écriture immuable (DEBIT ou CREDIT). |
| **JournalEntry** | Groupe atomique de postings. Doit balancer par asset. |
| **Balance** | Solde courant d'un compte. Projection cachée des postings. |

### 3.2 Hiérarchie de types

```java
public sealed interface Asset permits Currency, Security {
    String code();
    String name();
    int decimals();
}

public record Currency(String code, String name, int decimals) implements Asset { }

public final class Security implements Asset {
    private UUID id;
    private String isin;          // identifiant mondial (US0378331005)
    private String ticker;        // symbole local (AAPL)
    private String mic;           // exchange ISO 10383 (XNAS, XSWX)
    private String name;
    private SecurityType type;    // EQUITY, ETF, BOND, FUTURE, OPTION
    private Currency quoteCurrency;
    private SecurityStatus status; // PENDING_LISTING, ACTIVE, SUSPENDED, HALTED, DELISTED
    private Instant listedAt;
    private Instant delistedAt;
    // override code() = ticker, name() = name, decimals() = 0 (phase 1: no fractional shares)
}
```

### 3.3 Money

```java
public record Money(long minorUnits, Asset asset) {

    public static Money of(BigDecimal amount, Asset asset) {
        long minor = amount.movePointRight(asset.decimals())
                           .setScale(0, RoundingMode.UNNECESSARY)
                           .longValueExact();
        return new Money(minor, asset);
    }

    public Money plus(Money other) {
        requireSameAsset(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), asset);
    }

    public Money minus(Money other) {
        requireSameAsset(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), asset);
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(minorUnits).movePointLeft(asset.decimals());
    }

    private void requireSameAsset(Money other) {
        if (!asset.equals(other.asset)) {
            throw new IllegalArgumentException(
                "Cannot operate on different assets: " + asset.code() + " vs " + other.asset.code());
        }
    }
}
```

**Règles strictes :**
- Tous les montants stockés en **minor units BIGINT** (centimes, satoshis, actions entières)
- `Math.addExact` / `subtractExact` : fail-fast sur overflow
- `RoundingMode.UNNECESSARY` : force l'appelant à gérer explicitement les arrondis
- Aucune opération entre assets différents

### 3.4 Account

```java
public class Account {
    private UUID id;                     // UUID v7
    private AccountType type;            // CLIENT_CASH, CLIENT_POSITION, LIBRA_FEES,
                                          // LIBRA_CAPITAL, MARKET_COUNTERPARTY,
                                          // FX_COUNTERPARTY, NOSTRO, SUSPENSE
    private AccountStatus status;        // OPEN, CLOSED, FROZEN, PENDING_ACTIVATION
    private boolean pending;             // true = compte pending (engagement non settled),
                                          // false = compte final/settled
    private UUID customerId;             // nullable (null pour comptes non-client)
    private Asset asset;                 // un compte = un asset, immuable après création
    private String name;                 // label humain
    private Instant createdAt;
    private Instant closedAt;            // nullable
}
```

**Convention de pairage pending ↔ final** : pour chaque `(ownerId, asset, type)` métier, il existe **deux comptes** — un compte `pending=true` qui matérialise les engagements T+0, et un compte `pending=false` qui matérialise la position settled. Naming convention recommandée : `{owner}_{type}_{asset}` et `{owner}_{type}_{asset}_pending` (le label libre, pas une contrainte enum).

### 3.5 JournalEntry

```java
public class JournalEntry {
    private UUID id;                     // UUID v7
    private long sequenceNumber;         // monotone, auto-incrémenté, source d'ordre total
    private EntryType entryType;         // DEPOSIT, WITHDRAWAL, FX_TRADE,
                                          // EQUITY_BUY, EQUITY_SELL, DIVIDEND, FEE,
                                          // CORPORATE_ACTION
    private EntryPhase phase;            // BOOKING (T+0, postings sur comptes pending)
                                          // | SETTLEMENT (T+2, transfert pending → finaux)
                                          // | IMMEDIATE (T+0 et terminal, pas de phase pending)
    private Instant occurredAt;          // moment du fait métier (UTC)
    private String description;          // narration humaine
    private UUID causedBy;               // command/order/event source qui a déclenché ; pour une entry
                                          // SETTLEMENT, référence l'id de l'entry BOOKING libérée
    private EntryStatus status;          // POSTED, REVERSED
    private List<Posting> postings;      // au moins 2, équilibrés par asset
}
```

**Distinction `entryType` ↔ `phase`** : `entryType` répond à *"quelle opération métier ?"* (un equity buy, un trade FX, un dividende). `phase` répond à *"où dans son cycle de vie ?"* (booking T+0 sur pending, settlement T+2 vers finaux, ou immédiat sans phase pending). Les deux sont **orthogonaux** — un même `EQUITY_BUY` génère une entry `phase=BOOKING` puis une entry `phase=SETTLEMENT` à T+2.

`EntryPhase = IMMEDIATE` couvre les opérations qui n'ont pas de cycle T+2 : dépôt/retrait cash externe, frais Libra capturés immédiatement, dividende crédité, etc.

### 3.6 Posting

```java
public class Posting {
    private UUID id;                     // UUID v7
    private UUID journalEntryId;         // FK parent
    private int lineNumber;              // position dans l'entry (1-N)
    private UUID accountId;              // compte affecté
    private PostingType type;            // DEBIT ou CREDIT
    private Money amount;                // toujours positif, le sens vient de `type`
    private Money balanceAfter;          // snapshot solde post-application
}
```

**Invariants à valider à la construction :**
- `amount.minorUnits > 0`
- `amount.asset == account.asset` (cohérence)
- `balanceAfter.asset == amount.asset`

### 3.7 Balance (projection)

```java
public class Balance {
    private UUID accountId;              // PK
    private Money bookBalance;           // solde comptable
    private Money availableBalance;      // solde disponible
    private Money pendingDebits;         // engagements sortants non settled
    private Money pendingCredits;        // engagements entrants non settled
    private UUID lastPostingId;          // pour idempotence
    private long lastSequenceNumber;     // pour idempotence
    private Instant updatedAt;
}
```

**Invariants :**
- `availableBalance = bookBalance - pendingDebits + pendingCredits`
- Tous les `Money` de la Balance partagent le même `asset`

---

## 4. Règles métier critiques

### 4.1 Invariant fondamental de la double-entry

Pour chaque `JournalEntry`, et pour chaque asset impliqué :

```
SUM(postings où type=DEBIT  ET amount.asset=X) == SUM(postings où type=CREDIT ET amount.asset=X)
```

**Cette invariant doit être validé à la construction de la JournalEntry**, avant toute persistance. Une entry qui ne balance pas doit lever une exception.

### 4.2 Convention de signe

Pour Libra, convention **point de vue interne du broker** :
- Un compte client est un **passif** pour Libra (Libra doit cet argent au client)
- **CREDIT** sur un compte client → augmente ce que le client possède
- **DEBIT** sur un compte client → diminue ce que le client possède

(Inverse de la convention bancaire classique vue côté client. À documenter dans une ADR.)

### 4.3 Comptes de contrepartie obligatoires

Aucun asset ne peut "apparaître" ou "disparaître" du ledger. Toute entrée/sortie passe par un compte de contrepartie :

| Type d'opération | Compte contrepartie |
|---|---|
| Dépôt cash externe | `MARKET_COUNTERPARTY` ou `NOSTRO` pour la devise |
| Achat/vente equity | `MARKET_COUNTERPARTY` pour l'instrument |
| Conversion FX | `FX_COUNTERPARTY` pour chaque devise |
| Frais Libra | `LIBRA_FEES` |
| Dividendes | `MARKET_COUNTERPARTY` pour la devise (issuer-side) |

### 4.3bis Comptes pending dédiés (two-phase booking T+2)

**Convention transverse** (cf `CLAUDE.md` §6) : tous les trades (FX et equity) sont settlés à T+2. Le ledger matérialise cette mécanique via des **comptes pending dédiés**, distincts des comptes finaux. C'est la matérialisation comptable de l'invariant `availableBalance = bookBalance − pendingDebits + pendingCredits`.

Pour chaque compte final impliqué dans un trade, **il existe un compte pending pairé** (`pending=true` sur l'`Account`) :

| Compte final | Compte pending pairé |
|---|---|
| `client42_cash_usd` | `client42_cash_usd_pending` |
| `client42_position_aapl` | `client42_position_aapl_pending` |
| `market_counterparty_usd` | `market_counterparty_usd_pending` |
| `market_counterparty_aapl` | `market_counterparty_aapl_pending` |
| `libra_fees_usd` | `libra_fees_usd_pending` |

**Cycle de vie d'un trade** :

1. **T+0, phase = BOOKING** : une `JournalEntry` est posée, mais **tous les postings tombent sur les comptes pending**. Aucun compte final n'est touché à ce stade. Le `bookBalance` des comptes finaux reste inchangé ; les soldes pending matérialisent l'engagement.
2. **T+2, phase = SETTLEMENT** : une seconde `JournalEntry` (avec `causedBy` = id de l'entry de booking) est posée pour **transférer les positions depuis les comptes pending vers les comptes finaux**. Le `bookBalance` des comptes finaux bouge à ce moment, et les comptes pending reviennent à zéro pour les positions concernées.

**Propriétés préservées** :

- **Immutabilité des postings** — aucun posting n'est jamais modifié ; deux postings successifs (booking puis settlement) racontent l'histoire complète.
- **Invariant double-entry** par asset, **dans chacune des deux phases** indépendamment.
- **Auditabilité directe** : *"quels engagements sont en attente au 15 mars à 14h ?"* devient une requête `SELECT … FROM postings p JOIN accounts a ON … WHERE a.pending = true AND p.created_at <= '…'`.

Pour les opérations sans cycle T+2 (`DEPOSIT`, `WITHDRAWAL`, `FEE` capturé immédiatement, `DIVIDEND` crédité direct), `phase = IMMEDIATE` et les postings tombent directement sur les comptes finaux.

### 4.4 Exemple : achat 10 AAPL à 293 USD avec 5 USD de frais

L'opération produit **deux** `JournalEntry` successives, espacées de T+2 jours.

#### Phase 1 — T+0 (booking) : `entryType=EQUITY_BUY, phase=BOOKING`

5 postings, tous sur des comptes pending :

| # | Account (pending=true) | Type | Amount |
|---|---|---|---|
| 1 | `market_counterparty_aapl_pending` | DEBIT | 10 AAPL |
| 2 | `client42_position_aapl_pending` | CREDIT | 10 AAPL |
| 3 | `client42_cash_usd_pending` | DEBIT | 2935 USD |
| 4 | `market_counterparty_usd_pending` | CREDIT | 2930 USD |
| 5 | `libra_fees_usd_pending` | CREDIT | 5 USD |

Vérification (invariant par asset, sur les seuls comptes pending) :
- AAPL : DEBIT 10 = CREDIT 10 ✅
- USD : DEBIT 2935 = CREDIT 2930 + 5 ✅

À ce stade : `client42_cash_usd.bookBalance` est **inchangé** ; `client42_cash_usd.pendingDebits = 2935 USD` (dérivé du compte pending). `availableBalance` du compte cash final descend de 2935 USD, empêchant le double-spending sur un autre trade en parallèle.

#### Phase 2 — T+2 (settlement) : `entryType=EQUITY_BUY, phase=SETTLEMENT, causedBy=<id de l'entry de booking>`

10 postings, qui libèrent chaque pending et écrivent sur le compte final correspondant :

| # | Account | Type | Amount |
|---|---|---|---|
| 1  | `market_counterparty_aapl_pending` | CREDIT | 10 AAPL |
| 2  | `market_counterparty_aapl` (final)  | DEBIT  | 10 AAPL |
| 3  | `client42_position_aapl_pending`    | DEBIT  | 10 AAPL |
| 4  | `client42_position_aapl` (final)    | CREDIT | 10 AAPL |
| 5  | `client42_cash_usd_pending`         | CREDIT | 2935 USD |
| 6  | `client42_cash_usd` (final)         | DEBIT  | 2935 USD |
| 7  | `market_counterparty_usd_pending`   | DEBIT  | 2930 USD |
| 8  | `market_counterparty_usd` (final)   | CREDIT | 2930 USD |
| 9  | `libra_fees_usd_pending`            | DEBIT  | 5 USD |
| 10 | `libra_fees_usd` (final)            | CREDIT | 5 USD |

Vérification (invariant par asset, sur l'ensemble des comptes touchés par l'entry de settlement) :
- AAPL : DEBIT (2 + 3) = 20 ; CREDIT (1 + 4) = 20 ✅
- USD : DEBIT (6 + 7 + 9) = 5870 ; CREDIT (5 + 8 + 10) = 5870 ✅

Après cette entry, tous les comptes pending impliqués sont revenus à un solde nul pour ce trade ; les `bookBalance` des comptes finaux reflètent la position post-trade.

**Note pédagogique pitch Swissquote** : ce double passage `booking → settlement` est exactement le pattern utilisé chez TigerBeetle (`pending_transfers` comme primitive de premier ordre) et chez Stripe (séparation *posted* / *available*). Il préserve l'**immutabilité event-sourcing** des postings *et* matérialise la réalité métier du settlement T+2 sur les nostros — sans flag mutable.

---

## 5. Architecture interne du module

### 5.1 API exposée aux autres modules (port)

```java
package io.libra.ledger.api;

public interface LedgerService {

    /** Pose une JournalEntry complète. Valide l'invariant double-entry. Transactionnel. */
    PostedEntry postJournalEntry(PostJournalEntryCommand command);

    /** Lit le solde courant d'un compte. */
    Optional<Balance> getBalance(UUID accountId);

    /** Crée un compte. */
    Account openAccount(OpenAccountCommand command);

    /** Modifie le statut d'un compte. */
    void updateAccountStatus(UUID accountId, AccountStatus newStatus);

    /** Référentiel : lookup d'un asset par code. */
    Optional<Asset> findAsset(String code);
}
```

Commands et résultats sont des **records** immuables.

### 5.2 Events publiés

Tous les events sont publiés via Spring Modulith (Transactional Outbox automatique).

```java
package io.libra.ledger.events;

public record JournalEntryPosted(
    UUID entryId,
    long sequenceNumber,
    EntryType entryType,
    Instant occurredAt,
    UUID causedBy,
    List<PostingSummary> postings  // accountId, type, amount, asset
) { }

public record AccountOpened(UUID accountId, AccountType type, String assetCode, UUID customerId, Instant openedAt) { }

public record AccountStatusChanged(UUID accountId, AccountStatus previous, AccountStatus current, Instant changedAt) { }
```

### 5.3 Schéma DB (Flyway)

Tables principales :

```sql
-- Référentiel statique, seedé via Flyway
CREATE TABLE currencies (
    code        VARCHAR(3) PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    decimals    SMALLINT NOT NULL
);

CREATE TABLE securities (
    id              UUID PRIMARY KEY,
    isin            VARCHAR(12) NOT NULL UNIQUE,
    ticker          VARCHAR(16) NOT NULL,
    mic             VARCHAR(4) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(16) NOT NULL,
    quote_currency  VARCHAR(3) NOT NULL REFERENCES currencies(code),
    status          VARCHAR(20) NOT NULL,
    listed_at       TIMESTAMPTZ NOT NULL,
    delisted_at     TIMESTAMPTZ
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    account_type    VARCHAR(32) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    customer_id     UUID,
    asset_type      VARCHAR(10) NOT NULL,    -- 'CURRENCY' | 'SECURITY'
    asset_code      VARCHAR(20) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    closed_at       TIMESTAMPTZ
);
CREATE INDEX idx_accounts_customer ON accounts(customer_id);
CREATE INDEX idx_accounts_asset    ON accounts(asset_type, asset_code);

CREATE TABLE journal_entries (
    id              UUID PRIMARY KEY,
    sequence_number BIGSERIAL UNIQUE NOT NULL,
    entry_type      VARCHAR(32) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    description     TEXT,
    caused_by       UUID,
    status          VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_je_occurred ON journal_entries(occurred_at);
CREATE INDEX idx_je_caused   ON journal_entries(caused_by);

CREATE TABLE postings (
    id                UUID PRIMARY KEY,
    journal_entry_id  UUID NOT NULL REFERENCES journal_entries(id),
    line_number       INT NOT NULL,
    account_id        UUID NOT NULL REFERENCES accounts(id),
    type              VARCHAR(8) NOT NULL,    -- 'DEBIT' | 'CREDIT'
    amount            BIGINT NOT NULL,        -- en minor units
    asset_type        VARCHAR(10) NOT NULL,
    asset_code        VARCHAR(20) NOT NULL,
    balance_after     BIGINT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (journal_entry_id, line_number)
);
CREATE INDEX idx_postings_account ON postings(account_id, created_at);
CREATE INDEX idx_postings_entry   ON postings(journal_entry_id);

CREATE TABLE balances (
    account_id           UUID PRIMARY KEY REFERENCES accounts(id),
    book_balance         BIGINT NOT NULL DEFAULT 0,
    available_balance    BIGINT NOT NULL DEFAULT 0,
    pending_debits       BIGINT NOT NULL DEFAULT 0,
    pending_credits      BIGINT NOT NULL DEFAULT 0,
    asset_type           VARCHAR(10) NOT NULL,
    asset_code           VARCHAR(20) NOT NULL,
    last_posting_id      UUID,
    last_sequence_number BIGINT NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 5.4 Stratégie de mise à jour des balances

**Phase 1 : synchrone, dans la même transaction que les postings.**

```java
@Transactional
public PostedEntry postJournalEntry(PostJournalEntryCommand cmd) {
    JournalEntry entry = JournalEntry.create(cmd);  // valide invariant double-entry
    entry.assignSequenceNumber(sequenceService.next());
    
    journalEntryRepo.save(entry);
    
    for (Posting p : entry.postings()) {
        Balance bal = balanceRepo.findByAccountIdForUpdate(p.accountId())  // SELECT FOR UPDATE
                                  .orElseGet(() -> Balance.zero(p.accountId(), p.amount().asset()));
        Balance updated = bal.apply(p);
        balanceRepo.save(updated);
        p.recordBalanceAfter(updated.bookBalance());
    }
    
    postingRepo.saveAll(entry.postings());
    
    events.publishEvent(new JournalEntryPosted(...));  // outbox via Spring Modulith
    
    return PostedEntry.from(entry);
}
```

**Pourquoi synchrone d'abord :**
- Cohérence forte garantie
- Plus simple à raisonner et débugger
- Pas de fenêtre de désynchronisation
- Migration possible vers async plus tard si profiling le justifie

### 5.5 Outbox et events

Utiliser `spring-modulith-events-jpa` (table `event_publication` créée automatiquement). Le republier vers Kafka via `spring-modulith-events-kafka`. Configuration en `application.yml` :

```yaml
spring:
  modulith:
    events:
      jdbc:
        schema-initialization:
          enabled: true
      republish-outstanding-events-on-restart: true
      externalization:
        enabled: true
```

### 5.6 Job de réconciliation

Job Spring `@Scheduled` (cron 0 0 3 * * *, 3h du matin) qui :
1. Pour chaque compte, recalcule la balance from scratch en agrégeant les postings
2. Compare au snapshot stocké dans `balances`
3. Logue un WARN + métrique Prometheus si divergence
4. **Ne corrige pas automatiquement** (alerter, ne pas masquer le bug)

Endpoint admin `POST /admin/ledger/rebuild-balance/{accountId}` pour reconstruction manuelle.

---

## 6. Tests requis

### 6.1 Tests unitaires

- `MoneyTest` : arithmétique, overflow, rounding, asset mismatch
- `JournalEntryTest` : validation invariant double-entry, refus des entries non balancées
- `BalanceTest` : application d'un posting, invariant available = book - pending_debits + pending_credits

### 6.2 Tests property-based (jqwik)

**Le test signature du projet** : prouver l'invariant double-entry sur des séquences aléatoires.

```java
@Property(tries = 1000)
void balanceInvariantHoldsForAnyValidSequence(@ForAll @From("validJournalEntries") List<JournalEntry> entries) {
    ledger.applyAll(entries);
    for (Account acc : allAccounts) {
        Balance b = ledger.getBalance(acc.id()).orElseThrow();
        long recomputed = sumPostings(acc.id());
        assertThat(b.bookBalance().minorUnits()).isEqualTo(recomputed);
    }
    // par asset : sum(debits) == sum(credits) globalement
    for (Asset asset : allAssets) {
        long debits  = sumAllPostings(asset, DEBIT);
        long credits = sumAllPostings(asset, CREDIT);
        assertThat(debits).isEqualTo(credits);
    }
}
```

### 6.3 Tests d'intégration (Testcontainers)

- Postgres réel : `LedgerServiceIT` (POST entry, lecture balance, sequence number monotone, FOR UPDATE)
- Kafka + Spring Modulith : `EventPublicationIT` (un `postJournalEntry` produit un event Kafka après commit)
- Rollback test : crash simulé après save postings, vérifier qu'aucune balance n'est polluée

### 6.4 Tests d'architecture (ArchUnit + Spring Modulith)

```java
@Test
void verifyModularStructure() {
    ApplicationModules modules = ApplicationModules.of(LibraApplication.class);
    modules.verify();  // pas de dépendance vers internal d'un autre module
}
```

---

## 7. Conventions de code

- **Records partout** où value object / event / command / DTO
- **Sealed interfaces** pour les hiérarchies fermées (Asset, types d'events)
- **Pattern matching** exhaustif en Java 21, pas de `default` artificiel
- **Pas de Lombok** dans le domaine (records suffisent) ; OK pour les entités JPA si nécessaire
- **Tous les timestamps en `Instant` UTC**, jamais `LocalDateTime`
- **Tous les montants en `Money`** (jamais `BigDecimal` ou `long` nu dans la signature publique)
- **Foreign keys par UUID**, pas de relation JPA `@ManyToOne` lourde sauf nécessité
- **Spotless** appliqué (Google Java Format)
- **Tests d'abord** sur l'invariant double-entry et Money

---

## 8. Definition of Done

Le module Ledger est livrable quand :

- [ ] Schéma Flyway versionné, idempotent, avec seed des principales devises (CHF, USD, EUR, JPY, GBP)
- [ ] Toutes les entités du modèle conceptuel implémentées avec leurs invariants validés
- [ ] `LedgerService` (interface API + impl interne) exposé via `@org.springframework.modulith.NamedInterface`
- [ ] Events `JournalEntryPosted`, `AccountOpened`, `AccountStatusChanged` publiés via outbox
- [ ] Tests unitaires + property-based + intégration verts
- [ ] Job de réconciliation `@Scheduled` opérationnel
- [ ] Endpoint admin de rebuild d'une balance
- [ ] Métriques Micrometer : `ledger.postings.count`, `ledger.balance.divergence.count`, `ledger.entry.posting.duration`
- [ ] ADRs versionnées dans `/docs/adr` :
    - ADR-001 : Modular monolith via Spring Modulith
    - ADR-002 : Monetary amounts as BIGINT minor units
    - ADR-003 : Sync balance projection (phase 1)
    - ADR-004 : Transactional Outbox via Spring Modulith
- [ ] README du module avec exemple "achat 10 AAPL" déroulé

---

## 9. Hors-scope explicite (à ne PAS implémenter)

- Logique d'ordres, exécution, matching (→ module `trading`)
- Logique de prix, market data (→ module `pricing`)
- Validation pré-trade (→ module `validation`)
- **Orchestration** du settlement T+2 batch (→ module `settlement`, qui *déclenche* la 2ème JournalEntry chaque matin). Le ledger lui-même **porte** la mécanique des comptes pending et l'API pour poster les deux phases — mais c'est `settlement` qui décide *quand* poser l'entry de SETTLEMENT.
- KYC, profil client (→ module `customer`)
- Auth, sécurité applicative (→ module `api`)
- Frontend Angular

---

## 10. Pour aller plus loin (références utiles)

- **DDIA** ch. 3 (event sourcing), ch. 7-8 (transactions, isolation), ch. 11 (stream processing)
- **TigerBeetle docs** — leur modèle d'accounts/transfers est une excellente référence pour le double-entry haute performance
- **Stripe Engineering Blog** — articles sur leur ledger interne
- **Martin Fowler** — *Patterns of Enterprise Application Architecture* : Money pattern, Accounting Entry
- **Spring Modulith docs** — pour l'outbox et la modularisation

---

*Document généré dans le cadre de la conception architecturale du projet Libra. À mettre à jour au fil de l'implémentation.*