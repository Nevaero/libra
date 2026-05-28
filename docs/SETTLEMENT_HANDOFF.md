# Libra — Settlement Module Hand-off

> Document de hand-off pour Claude Code (ou tout autre assistant) chargé de reprendre la conception et l'implémentation du module `settlement` de Libra.
>
> **État d'avancement** : data model + events conçus en tutorat ; **logique métier (BusinessDayCalculator, SettlementScheduler), schéma DB, port `SettlementService`, ADRs, retry policy non encore tranchés**.

---

## 1. Contexte du projet

Rappel court — voir `CLAUDE.md`, `CLAUDE_HANDOFF.md`, et les autres `*_HANDOFF.md` pour le contexte complet.

- Libra = broker multi-asset simplifié, side-project portfolio Swiss fintech.
- **Settlement T+2 = différenciateur central** de Libra (Physical Forex), désormais étendu **uniformément aux equities** (décision actée).
- Stack : Java 25, Spring Boot 4.0.6, Spring Modulith 2.0.6, Gradle, PostgreSQL 16, Spring Kafka.
- Mode de travail : **tutorat ping-pong**.

---

## 2. Responsabilité du module Settlement

Le module `settlement` est le **chef d'orchestre du timing T+2** de Libra. Il :

1. Consomme `TradeExecuted` (sealed, depuis `trading`) à T+0.
2. Calcule la **value date T+2 ouvrée** via `BusinessDayCalculator` (jointure des calendriers bancaires des devises impliquées).
3. Crée une **`SettlementInstruction` PENDING** persistante (idempotente sur `tradeId`).
4. Lance un **batch matinal** (`SettlementScheduler`, Spring `@Scheduled`) qui ramasse toutes les instructions arrivées à échéance et déclenche la 2ème JournalEntry (phase SETTLEMENT) côté ledger.
5. Publie **`TradeSettled` (sealed)**, `SettlementFailed`, `SettlementBatchCompleted` selon le résultat.
6. Maintient un **audit trail** des batchs via `SettlementBatch`.

Il **ne** fait **pas** :

- L'écriture comptable des postings — il appelle `LedgerService.postSettlementEntry(bookingEntryId)` qui sait dériver les postings SETTLEMENT depuis l'entry BOOKING.
- Le calcul des prix / quotes (`pricing`).
- La validation pré-trade (`validation`).
- La gestion des clients (`customer`).

`package-info.java` : `allowedDependencies = {"core", "trading", "ledger"}`.

---

## 3. Modèle conceptuel — état actuel

### 3.1 Vocabulaire

| Terme | Définition |
|---|---|
| **Value date** | Date à laquelle un trade est réellement settled — T+2 ouvré dans Libra. Calculée à partir de la trade date en sautant week-ends et jours fériés bancaires. |
| **Settlement instruction** | Engagement à settler un trade donné à une value date donnée. Aggregate root du module. |
| **Settlement batch** | Audit trail d'un run du scheduler matinal. |
| **Holiday calendar** | Référentiel des jours fériés *bancaires* par devise (CHF, USD, EUR, etc.). Distinct du calendrier d'*ouverture des marchés* (qui appartient à `pricing`). |
| **BusinessDayCalculator** | Service de calcul T+N — joint plusieurs `HolidayCalendar` pour un trade cross-currency (e.g. USD/JPY = `US ∪ JP`). |
| **Two-phase booking** | Cf `LEDGER_HANDOFF.md` §4.3bis. Settlement = la 2ème phase (transfert pending → finaux). |

### 3.2 `HolidayCalendar` et `Holiday` (dans `core`)

```java
public record Holiday(LocalDate date, String name) { }

public record HolidayCalendar(String id, String name, List<Holiday> holidays) {
    public HolidayCalendar {
        holidays = List.copyOf(holidays);
    }
}
```

**Choix archi** : ces VOs vivent dans `core/entities/calendar/`, pas dans `settlement/`. Justification : `pricing` aussi en aura besoin (heures d'ouverture marché — calendrier *différent* mais structure identique). Mutualiser le type évite la duplication. Chaque module consomme avec son propre `id` (e.g. `"CH-BANKING"` côté settlement, `"XSWX-MARKET"` côté pricing).

Naming convention des `id` à fixer en phase logique. Recommandation : `<JURISDICTION>-<TYPE>` (e.g. `"CH-BANKING"`, `"US-BANKING"`, `"EUR-TARGET2"`).

Pas d'enum `HolidayNature` en phase 1. Si HALF_DAY devient nécessaire (e.g. veille de Noël aux US), à ajouter sans casser le contrat.

### 3.3 `SettlementInstruction` (aggregate root)

```java
public record SettlementInstruction(
    UUID id,
    UUID tradeId,           // FK vers Trade côté trading
    UUID bookingEntryId,    // FK vers la JournalEntry de phase BOOKING côté ledger
    LocalDate valueDate,    // T+2 ouvré calculé par BusinessDayCalculator
    SettlementStatus status,
    Instant createdAt,
    Instant settledAt,      // non-null ssi status == SETTLED
    String failureReason    // non-null ssi status == FAILED
) { ... }
```

**Invariants croisés validés dans le compact constructor** (switch exhaustif sur l'enum, pas de `default`) :

- `PENDING` ⇒ `settledAt == null && failureReason == null`
- `SETTLED` ⇒ `settledAt != null && failureReason == null`
- `FAILED` ⇒ `settledAt == null && failureReason non-blank`

**Idempotence côté event handler** : un `UNIQUE(tradeId)` en DB empêche la création de 2 instructions pour le même Trade en cas de replay `TradeExecuted`.

### 3.4 `SettlementBatch` (audit trail)

```java
public record SettlementBatch(
    UUID id,
    LocalDate valueDate,            // la date qui a été ciblée par ce run
    Instant runAt,
    Instant completedAt,            // non-null ssi status != RUNNING
    long instructionsProcessed,
    long instructionsSucceeded,
    long instructionsFailed,
    BatchStatus status
) { ... }
```

**Invariants** :
- `RUNNING` ⇒ `completedAt == null` ; sinon `completedAt non-null`.
- `instructionsProcessed = instructionsSucceeded + instructionsFailed` (cohérence comptable du batch lui-même).

### 3.5 Enums

```java
public enum SettlementStatus { PENDING, SETTLED, FAILED }
public enum BatchStatus { RUNNING, COMPLETED, PARTIAL_FAILURE, FAILED }
```

**Pas de `RESCHEDULED`** : un reschedule = création d'une **nouvelle instruction** avec une nouvelle `valueDate`. L'instruction d'origine reste `FAILED` (audit immuable). Plus DDD, plus simple.

---

## 4. Events publiés

Tous via **Spring Modulith outbox**. Topics non encore décidés (voir §6).

### 4.1 Sealed `TradeSettled`

```java
public sealed interface TradeSettled permits FxTradeSettled, EquityTradeSettled {
    UUID tradeId();
    UUID settlementInstructionId();
    Instant occurredAt();
}

public record FxTradeSettled(UUID tradeId, UUID settlementInstructionId, Instant occurredAt) implements TradeSettled { }
public record EquityTradeSettled(UUID tradeId, UUID settlementInstructionId, Instant occurredAt) implements TradeSettled { }
```

**Pattern Event Notification** (IDs + timestamps), pas Event-Carried State Transfer. Justification : l'event signifie *"ce trade a atteint son état terminal"* ; le contenu utile (postings, montants) est déjà dans le ledger via la JournalEntry de SETTLEMENT que `ledger` aura posée. Pas de raison de dupliquer.

**Sealed** : cohérence avec `TradeExecuted` côté trading. Permet du pattern matching exhaustif côté consumer et deux topics Kafka distincts si voulu (`settlement.fx-trades` vs `settlement.equity-trades`).

### 4.2 `SettlementFailed`

```java
public record SettlementFailed(
    UUID settlementInstructionId,
    UUID tradeId,
    String reason,
    Instant occurredAt
) { }
```

Publié à chaque passage `PENDING → FAILED`. Consommé par audit + alerting + (éventuellement) un workflow de retry manuel back-office.

`reason` texte libre en phase 1 — à formaliser en enum si la cardinalité des causes se stabilise (`COUNTERPARTY_UNAVAILABLE`, `NOSTRO_INSUFFICIENT`, `LEDGER_REJECTED`, etc.).

### 4.3 `SettlementBatchCompleted`

```java
public record SettlementBatchCompleted(
    UUID batchId,
    LocalDate valueDate,
    BatchStatus finalStatus,
    long instructionsSucceeded,
    long instructionsFailed,
    Instant occurredAt
) { }
```

Publié à la fin de chaque run du scheduler matinal. Consommé par audit / reporting / projections UI.

### 4.4 Producers / consumers attendus

| Event | Publisher | Consumers attendus |
|---|---|---|
| `FxTradeSettled` / `EquityTradeSettled` | settlement (post-batch) | UI portfolio (rafraîchissement balance settled), audit |
| `SettlementFailed` | settlement (passage à FAILED) | UI alerting, back-office, audit, métrique `settlement.failure.rate` |
| `SettlementBatchCompleted` | settlement (fin de batch) | dashboard ops, reporting, métrique `settlement.batch.duration` |

### 4.5 Events consommés

| Event | Publisher | Action settlement |
|---|---|---|
| `FxTradeExecuted` | trading | Crée `SettlementInstruction(status=PENDING, valueDate=T+2)`. |
| `EquityTradeExecuted` | trading | Crée `SettlementInstruction(status=PENDING, valueDate=T+2)`. |

---

## 5. Décisions architecturales actées

| Décision | Choix | Justification |
|---|---|---|
| Settlement T+2 uniforme | FX **et** equity (cf `CLAUDE.md` §6) | Différenciateur Libra appliqué partout. Cohérence pédagogique. |
| Two-phase booking | Settlement = phase 2 (transfert pending → finaux) ; logique ledger | Settlement orchestre le *timing*, pas le *contenu* comptable. |
| Aggregate root | `SettlementInstruction` | Cycle de vie indépendant du `Trade` — un Trade peut avoir 1+ instructions (cas reschedule = nouvelle instruction). |
| Création de l'instruction | Par **`settlement` event handler** consommant `TradeExecuted` (Romain, Q52b) | Plus DDD. Idempotence via `UNIQUE(tradeId)` côté DB. |
| Calendrier dans `core` | `HolidayCalendar` + `Holiday` partagés (Romain, Q52a) | Évite duplication avec pricing (heures de marché). Chaque module utilise son propre `id`. |
| `TradeSettled` sealed | `FxTradeSettled` + `EquityTradeSettled` (Romain, Q52c) | Cohérence avec sealed `TradeExecuted`. Routing Kafka et pattern matching exhaustif. |
| Style des events | Event Notification (IDs + timestamps), pas Event-Carried | L'info utile est déjà côté ledger. Pas de duplication. |
| Batch matinal | Traite **toutes** les instructions `PENDING` avec `valueDate ≤ today` en une seule run (Romain, Q52d) | Efficient. Cohérent avec la décision originelle "batch par jour, pas event scheduled par trade". |
| `RESCHEDULED` retiré | Reschedule = nouvelle instruction avec nouvelle `valueDate` | Audit immuable, plus simple. L'instruction d'origine reste FAILED. |
| `SettlementStatus` à 3 états | `PENDING / SETTLED / FAILED` | Minimum nécessaire phase 1. |
| `BatchStatus` à 4 états | `RUNNING / COMPLETED / PARTIAL_FAILURE / FAILED` | Distingue *partial failure* (alerting requis) de *complete failure* (alerting critique). |
| Invariants croisés sur status | Validés via switch exhaustif dans compact constructor (PENDING/SETTLED/FAILED) | Make Illegal States Unrepresentable au niveau record. |
| Allowed dependencies | `{"core", "trading", "ledger"}` | Settlement consomme TradeExecuted (trading) et appellera LedgerService (ledger). |

---

## 6. Décisions ouvertes (à trancher en tutorat ou en phase logique)

### 6.1 `BusinessDayCalculator` — design et sources de calendrier

À concevoir :

```java
public interface BusinessDayCalculator {
    LocalDate addBusinessDays(LocalDate from, int days, HolidayCalendar... calendars);
    boolean isBusinessDay(LocalDate date, HolidayCalendar... calendars);
}
```

Pour un trade FX `USD/JPY` : value date = T+2 où **les deux** calendriers (`US-BANKING` ∪ `JP-BANKING`) sont ouvrés. Pour un trade equity AAPL : juste `US-BANKING` (ou `EUR-TARGET2` selon le contexte). La logique de quel calendrier appliquer par type de trade est à formaliser.

**Sources des données** : seedées via Flyway en phase 1 (CSV par devise, années 2025-2026), pluggable vers une source externe (Refinitiv, EDP) en phase 2.

**Argument pitch fort** : pure logique métier finance, testable en property-based (jqwik sur 100 000 dates aléatoires → jamais un weekend, jamais un jour férié, monotone). C'est le **test signature** du module.

### 6.2 `SettlementScheduler`

Spring `@Scheduled` matinal :

```java
@Scheduled(cron = "0 0 9 * * MON-FRI")
public void runDailyBatch() { ... }
```

Cron à fixer (9h CET ? 6h CET pour précéder l'ouverture marché ?). Lit toutes les `SettlementInstruction` `PENDING` avec `valueDate ≤ today`. Pour chaque : appelle `LedgerService.postSettlementEntry(bookingEntryId)`, marque l'instruction `SETTLED` ou `FAILED`, agrège dans le `SettlementBatch`.

**Politique de transaction** : chaque instruction = transaction indépendante (un échec ne bloque pas les autres). Le batch lui-même est tracké via `SettlementBatch` qui passe `PARTIAL_FAILURE` s'il y a au moins un échec.

### 6.3 Retry policy pour `FAILED`

Cas typiques d'échec :
- Counterparty unavailable temporairement.
- Nostro à court.
- Ledger rejette pour cohérence (très rare).

Politiques candidates :
- **No automatic retry phase 1** : un FAILED reste FAILED, intervention back-office manuelle pour créer une nouvelle instruction reschédulée.
- **Auto-retry exponentiel** : N tentatives sur N jours ouvrés avant manual escalation.
- **Hybride** : auto-retry sur causes recoverable (lock contention DB), manual sinon.

À trancher quand on attaquera la phase logique.

### 6.4 Port `SettlementService`

À concevoir :

```java
public interface SettlementService {
    Optional<SettlementInstruction> findByTradeId(UUID tradeId);
    SettlementInstruction reschedule(UUID failedInstructionId, LocalDate newValueDate, String operatorReason);
    SettlementBatch lastBatchFor(LocalDate valueDate);
    List<SettlementInstruction> pendingAt(LocalDate asOf);
    void forceSettle(UUID instructionId);  // intervention back-office manuelle
}
```

### 6.5 Endpoint admin `/admin/settlement/...`

- `POST /admin/settlement/{instructionId}/reschedule` : reschedule manuelle.
- `POST /admin/settlement/{instructionId}/force` : force settlement (pour cas exceptionnels).
- `GET /admin/settlement/batch/{date}` : voir le résultat d'un run.

### 6.6 Topics Kafka

Topics candidats :
- `settlement.fx-trades` (`FxTradeSettled`)
- `settlement.equity-trades` (`EquityTradeSettled`)
- `settlement.failures` (`SettlementFailed`)
- `settlement.batches` (`SettlementBatchCompleted`)

OU un seul topic `settlement.lifecycle` pour tout. À fixer.

Clé de partitionnement : `tradeId` ou `settlementInstructionId` (ordre préservé par instruction).

### 6.7 Schéma DB (Flyway)

Tables candidates :
- `settlement_instructions` (UNIQUE sur `trade_id` pour idempotence ; index sur `(status, value_date)` pour le batch).
- `settlement_batches` (audit trail).
- `holiday_calendars` + `holidays` (référentiel, seedé via Flyway en phase 1).

### 6.8 ADRs à écrire

- ADR-021 : Two-phase booking côté settlement (orchestration sans posting direct)
- ADR-022 : `HolidayCalendar` dans `core` (partage settlement / pricing)
- ADR-023 : Reschedule = nouvelle instruction (pas de status RESCHEDULED)
- ADR-024 : Batch matinal vs scheduler par trade (décision originelle)

### 6.9 Tests requis (priorité haute)

- **Property-based** (jqwik) sur `BusinessDayCalculator` : sur 10 000 dates aléatoires, le résultat est toujours un jour ouvré, jamais < `from`, monotone.
- **Tests d'intégration end-to-end** : simulateur de horloge accélérée → un trade booké aujourd'hui est settled "demain" en temps test.
- **Tests de robustesse batch** : batch sur 10 000 instructions, certaines en échec, vérifier que les autres aboutissent.
- **Tests d'idempotence** : double consommation de `TradeExecuted` ne crée qu'une seule instruction (UNIQUE constraint vérifiée par test).

### 6.10 Métriques Micrometer à exposer

- `settlement.batch.duration` (histogramme)
- `settlement.batch.instructions.processed` (compteur tagué succeeded/failed)
- `settlement.instructions.pending.gauge` (snapshot du backlog)
- `settlement.lag` (`settled - valueDate`, doit rester proche de 0)
- `settlement.failure.rate` (par type de cause)

---

## 7. Conventions héritées (rappel transverse)

- **UUIDv7** partout, jamais v4.
- **`Instant` UTC** pour les timestamps, **`LocalDate`** pour les dates civiles (`valueDate`, `Holiday.date`).
- **Records** pour entités / events / DTOs ; **sealed interfaces** pour hiérarchies fermées.
- **Pattern matching exhaustif** sur les enums (pas de `default` artificiel — appliqué dans les compact constructors).
- **Outbox Spring Modulith** pour tout event externalisé.
- **Pas de Lombok dans le domaine** — records purs.
- **Two-phase booking** uniforme FX + equity (cf `CLAUDE.md` §6).

---

## 8. Hors-scope phase actuelle

- Logique de `BusinessDayCalculator`
- Implémentation du `SettlementScheduler` (cron, transactionnalité par instruction)
- Politique de retry pour les FAILED
- Port `SettlementService`
- Endpoints admin de reschedule / force-settle
- Schéma DB Flyway
- Seeding des `HolidayCalendar` (CSV par devise)
- Tests (property-based + intégration + ArchUnit)
- ADRs
- Métriques et observabilité
- Topics Kafka et partitioning
- Integration avec `LedgerService.postSettlementEntry(...)` (port à créer côté ledger)

---

## 9. État du code à ce point

```
io.libra.core.entities.calendar          (nouveau sous-package)
├── Holiday.java                          (record — date + name)
└── HolidayCalendar.java                  (record — id + name + List<Holiday>)

io.libra.settlement
├── package-info.java                     (@ApplicationModule, allowedDependencies={"core","trading","ledger"})
├── entities/
│   ├── SettlementInstruction.java        (aggregate root, invariants croisés)
│   ├── SettlementBatch.java              (audit trail, invariant comptable)
│   └── enums/
│       ├── SettlementStatus.java         (PENDING, SETTLED, FAILED)
│       └── BatchStatus.java              (RUNNING, COMPLETED, PARTIAL_FAILURE, FAILED)
└── events/
    ├── TradeSettled.java                 (sealed)
    ├── FxTradeSettled.java               (record)
    ├── EquityTradeSettled.java           (record)
    ├── SettlementFailed.java             (record)
    └── SettlementBatchCompleted.java     (record)
```

Aucun service implémenté, aucun calculator, aucun scheduler, aucune migration Flyway, aucun event handler.

---

## 10. Reprise de session — prochaines étapes

1. **Le data model des 7 modules est désormais complet** (`core`, `ledger`, `pricing`, `trading`, `customer`, `validation`, `settlement`).
2. Suites possibles :
    - **Implémentation `BusinessDayCalculator`** + tests property-based — *test signature* du module et meilleur argument pitch finance.
    - **Schéma DB Flyway** pour tous les modules (cohérence cross-module sur les conventions BIGINT, UUID, etc.).
    - **Ports `*Service`** pour les 6 modules métier (interfaces exposées via `@org.springframework.modulith.NamedInterface`).
    - **Premier flow end-to-end** : onboard customer → submit order → validation OK → trade booked → batch settle T+2 → balance updated. Test d'intégration Testcontainers.
    - **Simulateur de prix** côté pricing (random walk + mean reversion).
    - **API REST + WebSocket** côté `api/` (non créé encore).
    - **Angular frontend** (phase ultérieure du roadmap 6 semaines).
3. Ne **pas** implémenter spontanément — c'est un projet tutoré, sauf demande explicite de Romain.

---

*Document à jour à l'issue de la phase data model + events du module Settlement (post Q52 a/b/c/d). Le data model global de Libra est désormais complet.*
