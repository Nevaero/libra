# Libra — Pricing Module Hand-off

> Document de hand-off pour Claude Code (ou tout autre assistant) chargé de reprendre la conception et l'implémentation du module `pricing` de Libra.
>
> **État d'avancement** : data model + events conçus en tutorat ; logique métier, projection, schéma DB et topics Kafka **non encore tranchés** — laissés ouverts pour les prochaines sessions de tutorat.

---

## 1. Contexte du projet

Rappel court — voir `CLAUDE_HANDOFF.md` et `CLAUDE.md` pour le contexte complet :

- Libra = broker multi-asset simplifié, side-project portfolio Swiss fintech.
- Différenciateur : **Physical Forex avec settlement T+2** vs FX synthétique.
- Stack actuelle (à jour, divergente des handoffs précédents) : **Java 25**, Spring Boot 4.0.6, Spring Modulith 2.0.6, Gradle, PostgreSQL 16, Spring Kafka, Testcontainers, jqwik, ArchUnit.
- Mode de travail : **tutorat ping-pong**, pas implémentation autonome.

---

## 2. Responsabilité du module Pricing

Le module `pricing` est la **source de vérité market data** de Libra. Il :

1. Maintient le **référentiel des instruments tradables** (FX pairs et securities) — listing, statut, métadonnées.
2. Ingère / simule des **ticks de prix** (bid/ask + sizes) sur ces instruments.
3. Publie les **events** consommés par les autres modules : `PriceTick` pour le flux temps réel, `InstrumentListed` / `InstrumentStatusChanged` pour le cycle de vie des instruments.
4. Expose une **lecture synchrone du dernier prix** (projection `LatestQuote`) — sera consommée par `validation` pour les checks pré-trade et par `trading` pour la pricing d'un ordre marché.

Il **ne** sait **rien** sur :

- Les ordres (`trading`)
- Les écritures comptables (`ledger`)
- Les règles de validation pré-trade (`validation`)
- Les clients (`customer`)
- Les settlements (`settlement`)

`package-info.java` : `allowedDependencies = {"core"}`.

---

## 3. Modèle conceptuel — état actuel

### 3.1 Vocabulaire métier

| Terme | Définition |
|---|---|
| **Instrument** | Ce qui est cotable et tradable. Sealed : `CurrencyPair` ou `Security`. Défini dans `core`. |
| **CurrencyPair** | Paire FX orientée (base/quote). EUR/CHF = 0.93 signifie "1 EUR = 0.93 CHF". Value object. |
| **Security** | Titre coté (action, ETF, bond, future, option). Entity avec cycle de vie. |
| **Quote / Tick** | Snapshot bid/ask sur un instrument à un instant donné, par un provider. |
| **Bid / Ask** | Prix d'achat (bid) et de vente (ask) côté market. Le spread = source de revenus du broker. |
| **Size** | Quantité disponible à chacun des deux niveaux. |
| **Price scale** | Nombre de décimales du prix (5 pour EUR/CHF — pip + pipette, 2 pour USD/JPY, etc.). |
| **Tenor** | Maturité d'un quote FX (SPOT, TOD, TOM, 1W, 1M, 3M, 6M, 1Y). Pour une equity : convention `SPOT`. |
| **Provider** | Source du prix (counterparty FX, agrégateur, simulateur local). |
| **LatestQuote** | Projection lecture-seule : dernier tick connu par `(instrumentId, tenor)`. |

### 3.2 Hiérarchies sealed

#### Instrument (dans `io.libra.core.entities`)

```java
public sealed interface Instrument permits Security, CurrencyPair { }

public record CurrencyPair(
    UUID id,
    Currency baseCurrency,
    Currency quoteCurrency,
    CurrencyPairStatus status,
    int priceScale
) implements Instrument { }

public final class Security implements Asset, Instrument { /* @Data, voir core */ }
```

`Security` implémente **à la fois** `Asset` (pour le ledger : ce qu'on peut détenir) et `Instrument` (pour le pricing : ce qu'on peut coter). `Currency` implémente uniquement `Asset` — on ne cote pas une devise seule, on cote toujours une **paire**.

#### InstrumentStatus (dans `io.libra.core.entities.enums`)

Hiérarchie sealed pour unifier les statuts de cycle de vie d'un instrument, peu importe son type :

```java
public sealed interface InstrumentStatus permits CurrencyPairStatus, SecurityStatus { }

public enum CurrencyPairStatus implements InstrumentStatus {
    ACTIVE, SUSPENDED, DEACTIVATED
}

public enum SecurityStatus implements InstrumentStatus {
    PENDING_LISTING,    // annoncée, pas encore échangeable (avant IPO)
    ACTIVE,             // échangeable normalement
    SUSPENDED,          // temporairement arrêtée
    HALTED,             // arrêt intra-day par l'exchange (volatilité, news...)
    DELISTED            // définitivement retirée
}
```

**Pourquoi sealed plutôt qu'un seul enum unifié ?** Les états légaux divergent par type d'instrument (`PENDING_LISTING` ou `HALTED` n'ont aucun sens pour une paire FX). La sealed interface permet à l'event `InstrumentStatusChanged` de transporter n'importe quel statut tout en restant exhaustivement pattern-matchable côté consumer.

### 3.3 LatestQuote — projection lecture

```java
public record LatestQuote(
    UUID instrumentId,
    Tenor tenor,
    long bidMinorUnits,
    long askMinorUnits,
    long bidSize,
    long askSize,
    int priceScale,
    Instant quoteTime,
    Instant receivedAt,
    UUID providerId,
    long sequence
) { }
```

**Clé fonctionnelle : `(instrumentId, tenor)`**. Convention : pour une `Security`, on stocke avec `tenor = Tenor.SPOT`.

Détail des champs (rappel pour reprise de session) :

| Champ | Rôle |
|---|---|
| `instrumentId` | FK vers `Instrument` par UUID (pas de `@ManyToOne` lourd). |
| `tenor` | Discrimine les maturités FX. `SPOT` pour les equities. |
| `bidMinorUnits` / `askMinorUnits` | Prix stockés en entier minor units. Le `priceScale` localise la virgule. Pas de `Money` ici : un prix FX est un **ratio**, pas un montant. |
| `bidSize` / `askSize` | Profondeur top-of-book (un seul niveau, pas un order book complet). |
| `priceScale` | Voyage avec le quote — projection self-contained, robuste à un re-pricing de l'instrument. |
| `quoteTime` | Timestamp **émis par la source**. Ordering canonique du flux. |
| `receivedAt` | Timestamp **d'ingestion Libra**. `receivedAt - quoteTime` = latence (métrique). |
| `providerId` | UUID du provider source. Référence opaque, pas embed du record entier. |
| `sequence` | Numéro monotone par instrument. Sert (a) à ignorer les ticks out-of-order, (b) d'optimistic-lock sur l'UPSERT. |

### 3.4 Provider

```java
public record Provider(UUID id, String name, String code) { }
```

Référentiel statique pour l'instant (probablement seedé via Flyway plus tard). Phase 1 attendue : un seul provider = simulateur local (`code = "LIBRA_SIM"`).

### 3.5 Tenor

```java
public enum Tenor { TOD, TOM, SPOT, _1W, _1M, _3M, _6M, _1Y }
```

Sémantique FX :
- **TOD** : value date = jour J (cut-off intra-day strict).
- **TOM** : value date = J+1 ouvré.
- **SPOT** : value date = J+2 ouvré — c'est le **cœur du Physical Forex** de Libra.
- **_1W / _1M / _3M / _6M / _1Y** : forwards, hors-scope phase 1 (mais le data model les accommode déjà).

---

## 4. Events publiés

Tous les events seront publiés via **Spring Modulith outbox** (jamais directement vers Kafka). Les topics Kafka cibles ne sont **pas encore décidés** (voir §6).

### 4.1 PriceTick

```java
public record PriceTick(
    UUID id,
    Instrument instrument,
    long bidMinorUnits,
    long askMinorUnits,
    long bidSize,
    long askSize,
    Instant quoteTime,
    Instant receivedAt,
    Provider source,
    Tenor tenor,
    int priceScale
) { }
```

Event high-frequency. Sera consommé par `validation`, `trading` (pour pricer un MARKET order), projections UI (Angular WebSocket), et la projection `LatestQuote` elle-même.

**À discuter en tutorat** : le payload embarque l'`Instrument` entier et le `Provider` entier — c'est lourd pour un event high-frequency. Faut-il dégrader en `instrumentId: UUID` + `providerId: UUID` ? Trade-off : taille event vs autonomie du consumer (qui devrait sinon faire un lookup).

### 4.2 InstrumentListed

```java
public record InstrumentListed(UUID instrumentId, Instant listedAt, Provider source) { }
```

Event basse-fréquence cycle de vie. Un instrument devient disponible au pricing.

### 4.3 InstrumentStatusChanged

```java
public record InstrumentStatusChanged(
    UUID instrumentId,
    InstrumentStatus oldStatus,
    InstrumentStatus newStatus,
    String reason,
    Instant changedAt
) { }
```

Event basse-fréquence. Consommateurs : `validation` (bloquer un trade sur instrument SUSPENDED), UI (afficher l'état). Le `reason` est un texte libre — à formaliser plus tard si besoin (enum de motifs).

---

## 5. Décisions architecturales actées

| Décision | Choix | Justification |
|---|---|---|
| Distinction `Asset` vs `Instrument` | Deux sealed interfaces, `Security` implémente les deux | Une devise se détient mais ne se cote pas seule ; on cote des **paires**. |
| Statuts d'instrument | Sealed interface `InstrumentStatus` au lieu d'enum unifié | Les états légaux divergent par type (HALTED ≠ FX, DEACTIVATED ≠ equity). |
| Tenor par défaut pour equity | `Tenor.SPOT` | Évite `tenor = null` qui briserait la clé `(instrumentId, tenor)` de `LatestQuote`. |
| Représentation des prix | `long` minor units + `int priceScale` | Cohérent avec la convention BIGINT du ledger. Pas de `Money` car un prix = ratio, pas montant. |
| `priceScale` dans `LatestQuote` | Embarqué dans la projection | Self-contained ; robuste si l'instrument est re-paramétré. |
| Ordre des ticks | `sequence: long` monotone par instrument | Détection out-of-order + optimistic locking sur UPSERT. |
| Timestamps | Toujours `Instant` UTC, `quoteTime` (source) **et** `receivedAt` (ingestion) séparés | Permet de mesurer la latence d'ingestion et de détecter du "stale tick". |
| Outbox | Spring Modulith (JPA + Kafka externalization) | Cohérence transverse avec ledger ; jamais de publish Kafka direct. |
| FK par UUID | `providerId`, `instrumentId` dans les projections/events | Convention transverse — pas de `@ManyToOne` lourd. |

---

## 6. Décisions ouvertes (à trancher en tutorat)

Ces points n'ont **pas** encore été discutés. À reprendre quand la phase data model laisse place à la phase logique métier.

### 6.1 Topics Kafka et partitioning

Question à se poser en tutorat :

- Un topic dédié `pricing.ticks` pour les `PriceTick` ? Un topic séparé `pricing.instruments` pour le cycle de vie (`InstrumentListed`, `InstrumentStatusChanged`) — ils ont des cadences et des consumers très différents.
- Naming convention complète à fixer : `libra.pricing.v1.ticks`, `libra.pricing.v1.instrument-lifecycle`, ou plus court ?
- **Clé de partitionnement** : `instrumentId` (garantit l'ordre par instrument, partitions équilibrées tant qu'on a beaucoup d'instruments) ? Par classe d'asset ? Implications sur la parallélisation des consumers.
- Topic **compacté** ou non ? Un topic compacté ne garde que le dernier message par clé — convient pour `InstrumentStatusChanged` (le dernier état suffit) mais **pas** pour `PriceTick` (on perd l'historique intra-day si compacté agressivement).
- Rétention : combien de temps garder l'historique des ticks sur Kafka avant qu'il soit archivé ailleurs ?

### 6.2 Stratégie de projection LatestQuote

- Event handler synchrone (Spring Modulith) qui upsert sur réception d'un `PriceTick` ?
- Source du `sequence` : généré par le producer (`pricing`) à l'ingestion, ou utiliser l'offset Kafka ?
- Comment gérer le **bootstrap** d'un consumer (replay du topic au démarrage pour reconstruire `LatestQuote`) ?

### 6.3 Multi-provider

- Une ligne `LatestQuote` par `(instrumentId, tenor, providerId)` ? Ou une seule ligne agrégée représentant le "best price" ?
- Si agrégé : règle d'agrégation (median des bids, best bid/best ask cross-provider, weighted) ?
- Phase 1 attendue : **un seul provider** (simulateur local) → la question peut attendre.

### 6.4 Historique des prix

- Time-series Postgres standard, ou extension TimescaleDB ?
- Aggregation / compaction pour les charts longue durée (1m candles, 1h candles, daily) ?
- Rétention sur le chaud (Postgres) vs froid (S3/parquet) ?
- À décorréler du flux temps réel : pas de blocage sur ce point pour avancer.

### 6.5 Calendriers et heures d'ouverture

- FX : 24/5 (dimanche 22h UTC → vendredi 22h UTC) avec rollover daily.
- Equity : calendriers par MIC (XSWX, XNAS, XLON) avec jours fériés. Quelle source de calendrier ?
- Logique à placer où : `pricing` (ne génère pas de tick hors-ouverture), `validation` (bloque un trade hors-ouverture), ou les deux ?
- Concerne le **simulateur** plutôt que le data model des entités.

### 6.6 API exposée (port `PricingService`)

À concevoir sur le modèle de `LedgerService` :

```java
public interface PricingService {
    Optional<LatestQuote> getLatestQuote(UUID instrumentId, Tenor tenor);
    Optional<Instrument> findInstrument(UUID instrumentId);
    List<Instrument> listActiveInstruments();
    // commandes : registerInstrument, suspendInstrument, ingestTick (mock/admin) ?
}
```

### 6.7 Schéma DB (Flyway)

À écrire — tables candidates : `instruments` (ou éclatée `currency_pairs` + référentiel `securities` déjà au ledger), `latest_quotes`, `price_ticks_history` (optionnel), `providers`. À discuter : `securities` est déjà côté ledger dans le handoff — qui en est le **propriétaire canonique** ?

### 6.8 ADRs à écrire

- ADR-005 : pourquoi `Instrument` est sealed et distinct d'`Asset`
- ADR-006 : représentation des prix (long minor units + price scale, pas BigDecimal)
- ADR-007 : choix du partitioning Kafka pour les ticks
- ADR-008 : single-provider phase 1, ouverture vers multi-provider

### 6.9 Métriques Micrometer à exposer

- `pricing.tick.ingest.rate` (ticks/s par provider)
- `pricing.tick.latency` (`receivedAt - quoteTime`)
- `pricing.tick.outoforder.count` (ticks rejetés pour sequence < current)
- `pricing.latestquote.staleness` (`now - latestQuote.receivedAt` par instrument)

---

## 7. Conventions héritées (rappel transverse)

- **UUIDv7** partout, jamais v4.
- **`Instant` UTC**, jamais `LocalDateTime`.
- **Records** pour value objects / events / commands / DTOs.
- **Sealed interfaces** pour hiérarchies fermées.
- **BIGINT minor units** + champ `priceScale` séparé pour les prix ; **`Money`** pour les montants.
- **`Math.addExact`** / `subtractExact` sur tout calcul de quantité.
- **Pattern matching exhaustif** sur les sealed (pas de `default` artificiel).
- **FK par UUID**, pas de `@ManyToOne` lourd.
- **Outbox Spring Modulith** pour tout event externalisé.

---

## 8. Hors-scope phase actuelle

Sont explicitement **hors data model** pour cette phase :

- Logique d'ingestion / simulateur de prix (random walk avec mean reversion)
- Stratégie de projection (event handler, transactional outbox externalization)
- Schéma DB Flyway
- Endpoints REST / WebSocket
- Métriques et observabilité
- Tests (unitaires + jqwik + intégration + ArchUnit)
- Cross rates / triangulation FX
- Multi-provider et règles d'agrégation
- Calendriers d'ouverture
- ADRs
- README du module

Ces points seront repris en tutorat **après** la conception du module suivant (`trading`).

---

## 9. État du code à ce point

```
io.libra.core.entities
├── Asset.java                  (sealed)
├── Currency.java               (record, implements Asset)
├── CurrencyPair.java           (record, implements Instrument)
├── Instrument.java             (sealed, permits Security, CurrencyPair)
├── Security.java               (@Data class, implements Asset + Instrument)
├── Money.java                  (record)
└── enums/
    ├── InstrumentStatus.java   (sealed, permits CurrencyPairStatus, SecurityStatus)
    ├── CurrencyPairStatus.java
    ├── SecurityStatus.java
    └── SecurityType.java

io.libra.pricing
├── package-info.java           (@ApplicationModule, allowedDependencies={"core"})
├── entities/
│   ├── LatestQuote.java        (record — complété)
│   ├── Provider.java           (record)
│   └── enums/Tenor.java
└── events/
    ├── instrument/
    │   ├── InstrumentListed.java
    │   └── InstrumentStatusChanged.java
    └── price/
        └── PriceTick.java
```

**Petits défauts résiduels à acter ou corriger plus tard** (non bloquants pour la suite) :

- `Security` est annoté `@Data` (génère `getName()` + `setName()`) tout en exposant aussi `name()` via le contrat `Asset`. Double API cosmétique — divergence par rapport à la décision originelle "pas de Lombok dans le domaine". À trancher : revenir à des records / classes nues, ou acter `@Data`.
- Aucune entité n'est encore annotée `@Entity` JPA ; aucun repository ; aucun service ; aucune migration Flyway dans `src/main/resources/db/migration/`.

---

## 10. Reprise de session — prochaines étapes

1. La numérotation du tutorat continue : prochaine question = **Question 39** (déjà posée — narrative du cycle de vie d'un ordre, pour démarrer la conception du module **trading**).
2. Le module `pricing` est dans un état "data model + events posés, logique non conçue". On peut y revenir après `trading` pour reprendre §6.
3. Ne **pas** implémenter spontanément (JPA, Flyway, simulateur, projection) — c'est un projet tutoré.

---

*Document à jour à l'issue de la phase data model + events du module Pricing (post Question 38, pré-conception trading).*
