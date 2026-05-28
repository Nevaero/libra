# Libra — Trading Module Hand-off

> Document de hand-off pour Claude Code (ou tout autre assistant) chargé de reprendre la conception et l'implémentation du module `trading` de Libra.
>
> **État d'avancement** : ✅ **implémenté + testé (phase 1)**. Ce document conserve la conception d'origine ; les sections ci-dessous décrivent l'intention. **En cas de divergence, le code et `CLAUDE.md` font foi.** Pour le récapitulatif de ce qui a réellement été construit, voir l'encart « Implémentation » juste en dessous.
>
> ### Implémentation livrée (phase 1) — divergences vs cette conception
>
> - **Order unifié** : un seul record `Order` discriminé par `OrderType {MARKET, LIMIT}` + `limitPriceMinorUnits` nullable — **pas** la hiérarchie sealed `MarketOrder | LimitOrder` décrite plus bas.
> - **Mono-leg** : `ParentOrder` existe mais n'est **pas** utilisé ; `submitOrder` crée un seul `Order` standalone. Le multi-leg (split cross-currency) est repoussé en phase 2.
> - **Settlement synchrone** : trading **appelle** `settlement.scheduleSettlement(tradeId, bookingEntryId, tradeDate, assetClass)` dans sa TX ; il ne **publie pas** un event que settlement consomme. Donc pas de `ParentOrderSettled` ni de dépendance inversée. `allowedDependencies` en interfaces fines : `core`, `util`, `ledger :: port`/`domain`/`commands`, `pricing :: port`/`domain`, `validation :: port`/`domain`, `settlement :: port`/`domain`.
> - **Booking DvP** : le booking se fait via `TradeBooker` interne (deux legs équilibrés base + quote sur comptes pending), s'appuyant sur `LedgerService.resolve{Client,Counterparty}Account`. Phase BOOKING ; le batch settlement fait la phase 2.
> - **États terminaux** : `EXECUTED` / `REJECTED` (validation) / `CANCELLED` (no-fill). `SETTLED` n'est pas posé par trading en phase 1 (viendra du batch).
> - **Exécution** : `ExecutionSimulator` in-memory (fill au quote courant ; LIMIT marketable sinon no-fill). Pas de fills partiels.
>
> Spec canonique de l'implémentation : `CLAUDE.md` §4–5 + le code (`io.libra.trading`). La conception détaillée ci-dessous reste utile pour le *pourquoi* et pour la phase 2.

---

## 1. Contexte du projet

Rappel court — voir `CLAUDE.md`, `CLAUDE_HANDOFF.md`, `LEDGER_HANDOFF.md`, `PRICING_HANDOFF.md` pour le contexte complet.

- Libra = broker multi-asset simplifié, side-project portfolio Swiss fintech.
- Différenciateur : **Physical Forex avec settlement T+2**, **appliqué uniformément aussi aux equities** (décision actée en session).
- Stack : Java 25, Spring Boot 4.0.6, Spring Modulith 2.0.6, Gradle, PostgreSQL 16, Spring Kafka.
- Mode de travail : **tutorat ping-pong**, pas implémentation autonome.

---

## 2. Responsabilité du module Trading

Le module `trading` est le **chef d'orchestre des intentions clients**. Il :

1. Reçoit les **commandes** clients (`SubmitOrderCommand` — appel synchrone depuis l'API).
2. Garantit l'**idempotence** des commandes via une clé fournie par le client.
3. Décompose une intention client en **legs concrets** quand nécessaire (Physical FX cross-currency).
4. Appelle `validation` en **command synchrone** pour les checks pré-trade.
5. Simule l'exécution contre le marché (in-memory en phase 1), produit des `Trade`s.
6. Publie les **events** du cycle de vie : `OrderSubmitted`, `OrderAccepted`, `OrderRejected`, `TradeExecuted` (sealed), `ParentOrderSubmitted`, `ParentOrderSettled`.

Il **ne** sait **rien** sur :

- Les écritures comptables (`ledger`)
- Les prix de marché (`pricing` — il consomme via `PricingService`)
- Les règles de validation pré-trade (`validation` — il appelle via port sync)
- Les détails des clients (`customer` — il porte juste un `clientId` opaque)
- Les settlements (`settlement` — il publie des events que settlement consomme)

`package-info.java` : `allowedDependencies = {"core"}` *(à étendre quand on connectera pricing/validation/ledger via leurs ports publics)*.

---

## 3. Modèle conceptuel — état actuel

### 3.1 Vocabulaire métier

| Terme | Définition |
|---|---|
| **Order** | Intention client matérialisée. Sealed : `MarketOrder` ou `LimitOrder`. Immuable hors transitions de status. |
| **MarketOrder** | Exécution au prix marché courant. Pas de `limitPrice`. |
| **LimitOrder** | Exécution conditionnelle au franchissement d'un seuil de prix. Porte un `limitPriceMinorUnits`. |
| **Trade** | Fait d'exécution. Naît *après* l'Order. 1 Order peut générer N Trades (pattern **Order-Fill**). |
| **ParentOrder** | Aggregate root DDD. Porte l'intention métier originale (asset cible + asset source). Contient ≥ 1 child Order. |
| **Side** | `BUY` ou `SELL`. Tous les children d'un ParentOrder ont la même side que lui. |
| **OrderStatus** | État courant d'un Order ou d'un ParentOrder. État agrégé côté parent dérivé des children. |
| **Two-phase booking** | Booking T+0 sur comptes pending dédiés + settlement T+2 vers comptes finaux. Convention transverse, voir `CLAUDE.md` §6. |
| **Idempotency key** | UUID fourni par le client à la soumission, garantit la déduplication par `(clientId, idempotencyKey)` UNIQUE. |

### 3.2 Sealed `Order`

```java
public sealed interface Order permits MarketOrder, LimitOrder {
    UUID id();
    UUID idempotencyKey();
    UUID clientId();
    Instant submittedAt();
    Instrument instrument();
    Side side();
    Money quantity();
    OrderStatus status();

    static void validateQuantityAsset(Money quantity, Instrument instrument) {
        if (!quantity.asset().equals(instrument.baseAsset())) {
            throw new IllegalArgumentException(...);
        }
    }
}
```

**Pattern actés** :

- **Make Illegal States Unrepresentable** (Yaron Minsky / Scott Wlaschin) — sealed + records = ADT, le `limitPrice` n'existe pas sur `MarketOrder` au niveau du compilateur.
- **Invariant `quantity.asset() == instrument.baseAsset()`** — validé à la construction de chaque record concret (compact constructor appelle `Order.validateQuantityAsset`). Pour FX : convention universelle "BUY 1000 EUR/CHF" = acheter 1000 EUR (base).
- **Pas de champ `orderType`** — redondant avec le type sealed.
- **Pas de `sourceAccountId` / `destinationAccountId`** — le ledger résout les comptes à partir de `clientId` + `instrument`.
- **Pas de counterparty / executedPrice / executedAt** — ces champs vivent dans `Trade`.

### 3.3 `MarketOrder` / `LimitOrder`

```java
public record MarketOrder(
    UUID id, UUID idempotencyKey, UUID clientId,
    Instant submittedAt, Instrument instrument,
    Side side, Money quantity, OrderStatus status
) implements Order {
    public MarketOrder { Order.validateQuantityAsset(quantity, instrument); }
}

public record LimitOrder(
    UUID id, UUID idempotencyKey, UUID clientId,
    Instant submittedAt, Instrument instrument,
    Side side, Money quantity, OrderStatus status,
    long limitPriceMinorUnits
) implements Order {
    public LimitOrder {
        Order.validateQuantityAsset(quantity, instrument);
        if (limitPriceMinorUnits <= 0) throw new IllegalArgumentException(...);
    }
}
```

- **`limitPriceMinorUnits` (long)** — convention transverse minor units. Le `priceScale` est dérivable via `instrument` (cf `CurrencyPair.priceScale()` ou équivalent pour `Security`).
- **Pas de `Money` pour le prix** — un prix FX est un *ratio*, pas un montant.

### 3.4 `Trade`

```java
public record Trade(
    UUID id,
    UUID orderId,
    UUID counterpartyId,
    Money executedQuantity,
    long executedPriceMinorUnits,
    Instant executedAt
) {
    public Trade {
        if (executedQuantity.minorUnits() <= 0) throw ...;
        if (executedPriceMinorUnits <= 0) throw ...;
    }
}
```

**Distinction OMS/EMS** au niveau des entités (les deux fonctions cohabitent dans le module `trading` au niveau code) :

- `Order` = entité OMS-side. Connue *avant* exécution. Audit "qui a voulu acheter quoi".
- `Trade` = entité EMS-side. Naît *après* exécution. Audit "comment l'avons-nous exécuté, à quel prix, contre qui".
- Relation `1 Order → N Trades` (pattern Order-Fill, pour LIMIT partiellement remplis).

**Champs volontairement absents** : `instrument`, `side`, `clientId` (dérivables via `orderId`), `venue` / `MIC` (dérivable via `counterpartyId`), `limitPrice` (dans l'Order), `feeAmount` (les frais sont des postings ledger séparés).

### 3.5 `ParentOrder` — aggregate root

```java
public record ParentOrder(
    UUID id,
    UUID idempotencyKey,
    UUID clientId,
    Instant submittedAt,
    Side side,
    Money targetQuantity,       // intention finale, e.g. Money(10, AAPL)
    Asset sourceAsset,          // devise source du paiement, e.g. Currency CHF
    OrderStatus status,         // status agrégé dérivé des children
    List<UUID> childOrderIds
) {
    public ParentOrder {
        if (childOrderIds == null || childOrderIds.isEmpty()) throw ...;
        if (targetQuantity.minorUnits() <= 0) throw ...;
        if (targetQuantity.asset().equals(sourceAsset)) throw ...;
        childOrderIds = List.copyOf(childOrderIds);
    }
}
```

**Patterns actés** :

- **Aggregate Root** (DDD, Eric Evans) — porte les invariants et informations qu'aucun child ne porte (intention métier originale en `targetQuantity` + `sourceAsset`).
- **Single-aggregate transactional consistency** — la création parent+children est dans une seule transaction ACID Postgres. **Anti-pattern saga évité** : Libra est un modular monolith, donc transactions locales suffisent, pas besoin d'orchestration distribuée + compensation.
- **Uniformité parent-child** — même pour un trade single-leg (e.g. client en CHF qui trade EUR/CHF directement), un `ParentOrder` à 1 enfant est créé. Coût : 1 row supplémentaire. Bénéfice : entrée d'audit unique côté client.
- **Idempotency key au niveau parent** — attachée à l'**intention** client, pas aux child Orders générés par Libra.

**Invariants inter-entités à valider au niveau service** (le record ne les voit pas) :

- Tous les `childOrderIds` référencent des `Order` existants.
- Tous les children ont `side == parent.side`.
- Tous les children ont `clientId == parent.clientId`.
- Au moins un child a `instrument.baseAsset() == parent.targetQuantity.asset()` (le child qui acquiert le target final).
- Au moins un child a `instrument.quoteAsset() == parent.sourceAsset` (le child qui sort les fonds source).
- Le status parent est dérivé de l'agrégation des status des children (cf §3.6).

### 3.6 `OrderStatus` — state machine

```java
public enum OrderStatus {
    SUBMITTED, ACCEPTED, REJECTED, EXECUTED, CANCELLED, SETTLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case SUBMITTED -> next == ACCEPTED || next == REJECTED;
            case ACCEPTED  -> next == EXECUTED || next == CANCELLED;
            case EXECUTED  -> next == SETTLED;
            case REJECTED, CANCELLED, SETTLED -> false;
        };
    }

    public boolean isTerminal() {
        return switch (this) {
            case REJECTED, CANCELLED, SETTLED -> true;
            case SUBMITTED, ACCEPTED, EXECUTED -> false;
        };
    }
}
```

Diagramme :

```
                ┌────────────┐
                │ SUBMITTED  │
                └─────┬──────┘
            ┌─────────┴─────────┐
            ▼                   ▼
      ┌──────────┐        ┌──────────┐
      │ ACCEPTED │        │ REJECTED │ (terminal)
      └─────┬────┘        └──────────┘
       ┌────┴─────┐
       ▼          ▼
 ┌──────────┐  ┌────────────┐
 │ EXECUTED │  │ CANCELLED  │ (terminal)
 └─────┬────┘  └────────────┘
       ▼
 ┌──────────┐
 │ SETTLED  │ (terminal)
 └──────────┘
```

**Règle d'agrégation côté ParentOrder** (à formaliser en code) :

- `SUBMITTED` tant qu'au moins un child est dans cet état.
- `ACCEPTED` quand tous les children sont au moins `ACCEPTED`.
- `EXECUTED` quand tous les children sont au moins `EXECUTED`.
- `SETTLED` quand tous les children sont `SETTLED`.
- `REJECTED` si au moins un child est `REJECTED` (transition immédiate du parent, les autres children doivent être annulés).

**Hors-scope phase 1** : `PARTIALLY_FILLED` (LIMIT partiel), `EXPIRED` (LIMIT GTD/DAY), `REVERSED` (annulation back-office d'un trade settled).

---

## 4. Events publiés

Tous via **Spring Modulith outbox** (jamais publish Kafka direct). Topics non encore décidés (voir §6.1). Pattern **Event-Carried State Transfer** (Fowler) — chaque event transporte l'entity complète.

### 4.1 Events `Order` (child-level)

```java
public record OrderSubmitted(Order order, Instant occurredAt) { }
public record OrderAccepted(Order order, Instant occurredAt) { }
public record OrderRejected(Order order, String reason, Instant occurredAt) { }
```

### 4.2 Event `TradeExecuted` (sealed)

```java
public sealed interface TradeExecuted permits FxTradeExecuted, EquityTradeExecuted {
    Trade trade();
    Instant occurredAt();
}

public record FxTradeExecuted(Trade trade, Instant occurredAt) implements TradeExecuted { }
public record EquityTradeExecuted(Trade trade, Instant occurredAt) implements TradeExecuted { }
```

**Choix archi** : sealed + 2 records identiques pour l'instant. La distinction est sémantique et sert au **routing** Kafka (deux topics distincts à terme) et au pattern matching exhaustif côté consumer. Évolution possible : ajout de champs spécifiques (`valueDate` calculé) sans casser le contrat sealed.

### 4.3 Events `ParentOrder` (root-level)

```java
public record ParentOrderSubmitted(ParentOrder parentOrder, Instant occurredAt) { }
public record ParentOrderSettled(ParentOrder parentOrder, Instant occurredAt) { }
```

Concept officiel : **coarse-grained events à l'aggregate root, fine-grained events aux entities**. Évite l'event spam, donne deux résolutions d'audit (intention vs leg). Pour la phase 1, uniquement chemin nominal `Submitted → Settled` — les rejets restent traçables via les events children.

### 4.4 Producers / consumers attendus

| Event | Publisher | Consumers attendus |
|---|---|---|
| `ParentOrderSubmitted` | trading (à la réception du command) | validation (sync command en parallèle), audit, UI |
| `OrderSubmitted` | trading (un par child généré) | audit, UI |
| `OrderAccepted` | trading (après validation OK) | EMS interne, ledger pre-trade prep |
| `OrderRejected` | trading (validation KO ou rejet métier) | UI, audit |
| `FxTradeExecuted` / `EquityTradeExecuted` | trading (matching réussi) | ledger (booking T+0), settlement (planning T+2) |
| `ParentOrderSettled` | trading (quand tous children settled) | UI, audit, projections portfolio |

---

## 5. Décisions architecturales actées

| Décision | Choix | Justification |
|---|---|---|
| Distinction Order vs Trade | Deux entités séparées | Pattern Order-Fill : 1 Order → N Trades. OMS vs EMS au niveau entité. |
| Type Market vs Limit | Sealed `Order` + records | Make Illegal States Unrepresentable. `limitPrice` n'existe pas sur MarketOrder au niveau type. |
| Idempotence | UUID `idempotencyKey` fourni par le client + UNIQUE `(clientId, idempotencyKey)` en DB | Race-condition-proof (vs SELECT-then-INSERT applicatif). Pattern Stripe. La 2ème requête renvoie la même réponse. |
| Multi-leg cross-currency | `ParentOrder` aggregate root + N child Orders | DDD aggregate + transaction ACID locale. Anti-saga (modular monolith). |
| ParentOrder vs Order standalone | `ParentOrder` à 1 enfant **même pour les single-leg** | Uniformité du modèle, entrée d'audit unique côté client. |
| Settlement T+2 uniforme | Appliqué à FX **et** equity | Différenciateur Libra. Two-phase booking via comptes pending dédiés (cf `CLAUDE.md` §6). |
| Représentation des prix | `long` minor units, `priceScale` dérivé de l'instrument | Cohérent avec convention pricing/ledger. Pas de `Money` pour un prix (ratio). |
| Quantité | `Money quantity` avec asset = `instrument.baseAsset()` | Cohérent avec convention universelle FX. Validé à la construction. |
| Resolution des comptes | Trading ne porte pas d'`accountId` ; ledger résout via `(clientId, asset)` | Cohésion : le ledger est l'authority of record sur les comptes. |
| Counterparty | `counterpartyId` UUID sur `Trade`, pas sur Order | L'info naît à l'exécution, pas à la soumission. |
| Style des events | Event-Carried State Transfer (entity complète) | Consumers self-contained, cohérent avec `PriceTick` (pricing). |
| Granularité events | Coarse côté ParentOrder, fine côté child Orders | Évite event spam, audit à deux résolutions. |
| Sealed `TradeExecuted` | `permits FxTradeExecuted, EquityTradeExecuted` | Pattern matching exhaustif consumer + topics Kafka distincts. |
| State machine | Enum `OrderStatus` + `canTransitionTo()` switch exhaustif | Validation côté service. Pas de transition illégale au runtime. |

---

## 6. Décisions ouvertes (à trancher en tutorat)

### 6.1 Topics Kafka et partitioning

Aucun topic décidé. À discuter :

- Topics candidats : `trading.parent-orders.lifecycle`, `trading.orders.lifecycle`, `trading.trades.fx`, `trading.trades.equity`.
- Naming convention complète à fixer (`libra.trading.v1.*` ?).
- **Clé de partitionnement** : `clientId` (préserve l'ordre par client) ou `parentOrderId` (préserve l'ordre de chaque intention) ? Implications sur la parallélisation.
- Topics compactés pour les lifecycles (dernier état suffit) ou non (rétention complète des transitions pour audit) ?

### 6.2 Schéma DB (Flyway)

Tables candidates : `parent_orders`, `orders` (avec discriminator `order_type` pour MARKET/LIMIT), `trades`. Index UNIQUE sur `(client_id, idempotency_key)` pour les deux niveaux (parent et child séparés).

### 6.3 Port `TradingService`

À concevoir sur le modèle de `LedgerService` :

```java
public interface TradingService {
    SubmittedOrderResult submitOrder(SubmitOrderCommand cmd);
    Optional<ParentOrder> findParentOrder(UUID id);
    Optional<Order> findOrder(UUID id);
    List<Trade> tradesOfOrder(UUID orderId);
    void cancelOrder(UUID orderId);
}
```

### 6.4 Stratégie d'exécution (EMS interne)

- Simulateur d'exécution in-memory (MARKET au mid courant, LIMIT déclenché par tick).
- Comment et quand publier `TradeExecuted` ?
- Liaison avec `pricing` via `PricingService.getLatestQuote(...)`.

### 6.5 Idempotence et déduplication des Trades

Si un `OrderAccepted` est consommé deux fois (retry Kafka), il ne doit pas générer deux Trades. Mécanisme : index UNIQUE sur `trades(order_id, ...)` ou contrôle applicatif ?

### 6.6 Cancellation et reversal

- Comment annuler un Order `ACCEPTED` non encore exécuté ? Command sync + transition d'état.
- Comment "reverser" un Trade déjà `SETTLED` (cas back-office) ? Probablement un Order opposé + journal entry de réversion (cf `JournalEntry.entryType = REVERSED`).

### 6.7 TimeInForce et états additionnels

- `TimeInForce` enum (DAY, GTC, IOC, FOK) — non porté actuellement, à ajouter sur `LimitOrder`.
- États `PARTIALLY_FILLED`, `EXPIRED`, `REVERSED` à ajouter dans `OrderStatus` quand on attaquera les LIMIT proprement.

### 6.8 ADRs à écrire

- ADR-009 : Aggregate root ParentOrder (DDD) + anti-saga (modular monolith)
- ADR-010 : Sealed `Order` + ADT (Make Illegal States Unrepresentable)
- ADR-011 : Idempotency Key au niveau DB (UNIQUE constraint)
- ADR-012 : Settlement T+2 uniforme FX + equity (et révision rétroactive du `LEDGER_HANDOFF.md`)

### 6.9 Métriques Micrometer à exposer

- `trading.orders.submitted.count` (par type, par side)
- `trading.orders.rejected.count` (par raison)
- `trading.trades.executed.count` (par instrument)
- `trading.parent_orders.legs.histogram` (nombre de legs par parent)
- `trading.duration.submit_to_settle` (latence end-to-end)

### 6.10 Dépendances inter-modules à étendre

`trading/package-info.java` doit ajouter à `allowedDependencies` :
- `pricing` (pour `PricingService.getLatestQuote(...)`)
- `validation` (pour `ValidationService.validate(...)`)
- `ledger` (pour `LedgerService.postJournalEntry(...)`)

À faire au moment d'implémenter ces appels, pas avant.

---

## 7. Conventions héritées (rappel transverse)

- **UUIDv7** partout, jamais v4.
- **`Instant` UTC**, jamais `LocalDateTime`.
- **Records** pour value objects / events / commands / DTOs.
- **Sealed interfaces** pour hiérarchies fermées (`Order`, `TradeExecuted`).
- **BIGINT minor units** + `priceScale` dérivé de l'instrument pour les prix ; **`Money`** pour les montants.
- **Pattern matching exhaustif** sur les sealed et les enums (pas de `default` artificiel).
- **FK par UUID**, pas de `@ManyToOne` lourd.
- **Outbox Spring Modulith** pour tout event externalisé.
- **Pas de Lombok dans le domaine** — records et classes nues (alignement décision originelle ; divergence `@Data` sur `Security` à acter séparément).

---

## 8. Hors-scope phase actuelle

Sont explicitement **hors data model** pour cette phase :

- Logique d'exécution (simulateur, matching, MARKET/LIMIT execution rules)
- Décomposition multi-leg (de `ParentOrder` → child Orders, calcul des quantités/prix indicatifs)
- Validation pré-trade (déléguée au module `validation`)
- Posting des journal entries (délégué au module `ledger`)
- Schéma DB Flyway
- Endpoints REST `/orders`, `/parent-orders`
- Topics Kafka et partitioning
- Métriques et observabilité
- Tests (unitaires + property-based sur la state machine + intégration + ArchUnit)
- ADRs
- README du module
- Cancellation et reversal logic
- TimeInForce, ordres avancés (stop, trailing stop, etc.)
- Partial fills (LIMIT)

Ces points seront repris en tutorat **après** la conception des modules suivants (`customer`, `validation`).

---

## 9. État du code à ce point

```
io.libra.trading
├── package-info.java                         (@ApplicationModule, allowedDependencies={"core"})
├── entities/
│   ├── Order.java                            (sealed interface)
│   ├── MarketOrder.java                      (record)
│   ├── LimitOrder.java                       (record)
│   ├── Trade.java                            (record)
│   ├── ParentOrder.java                      (record, aggregate root)
│   └── enums/
│       ├── Side.java                         (BUY, SELL)
│       └── OrderStatus.java                  (6 états + canTransitionTo + isTerminal)
└── events/
    ├── OrderSubmitted.java                   (record)
    ├── OrderAccepted.java                    (record)
    ├── OrderRejected.java                    (record)
    ├── TradeExecuted.java                    (sealed)
    ├── FxTradeExecuted.java                  (record)
    ├── EquityTradeExecuted.java              (record)
    ├── ParentOrderSubmitted.java             (record)
    └── ParentOrderSettled.java               (record)
```

**Petits défauts résiduels à acter ou corriger plus tard** :

- `trading/package-info.java` contient un commentaire `// pricing/package-info.java` copié de pricing (cosmétique).
- Aucune entité JPA, aucun repository, aucune migration Flyway.
- Aucun port `TradingService` exposé via `@org.springframework.modulith.NamedInterface`.
- `LEDGER_HANDOFF.md` §4.4 (exemple "achat 10 AAPL") devient **désynchronisé** avec la décision T+2 uniforme : l'exemple écrit directement sur les comptes finaux sans phase pending. À reprendre au moment d'aligner le ledger.

---

## 10. Reprise de session — prochaines étapes

1. Numérotation tutorat : prochaine question = **Question 51**.
2. Le data model `trading` est **complet** pour la phase 1.
3. Suites possibles :
    - Concevoir le module `customer` (léger, courte session) — débloque `validation` ensuite.
    - Concevoir le module `validation` (dépend de pricing + ledger + customer).
    - Reprendre `pricing` ou `ledger` sur les zones laissées ouvertes (topics Kafka, schéma DB, projections).
    - Implémenter (sortir du tutorat) : schéma DB Flyway pour trading, port `TradingService`, simulateur d'exécution.
4. Ne **pas** implémenter spontanément (JPA, Flyway, simulateur) — c'est un projet tutoré.

---

*Document à jour à l'issue de la phase data model + events du module Trading (Q39 à Q50).*
