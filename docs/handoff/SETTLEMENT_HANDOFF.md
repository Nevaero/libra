# Libra — Settlement Module Hand-off

> Document de hand-off pour le module `settlement` de Libra.
>
> **État** : implémenté et testé (`BusinessDayCalculator` + `SettlementService` schedule/batch + scheduler). Suite verte (property-based jqwik + Testcontainers). Reste : calendriers réels depuis `reference`, retry policy, reschedule/force-settle, métriques, ADRs, endpoints admin.

---

## 1. Responsabilité

Le **chef d'orchestre du timing T+2** de Libra. Il crée une `SettlementInstruction` (engagement à settler un trade à une value date T+2 ouvrée), et un **batch matinal** déclenche la 2ème phase comptable (transfert pending → finaux) via le ledger. Le différenciateur Physical Forex, appliqué uniformément FX **et** equity.

`allowedDependencies = {"core", "ledger"}`. Domaine sous `settlement.domain` (records), persistence ACL.

## 2. Décision structurante : modèle SYNCHRONE (révise le handoff)

Le handoff de conception (Q52b) avait choisi **event-driven** (settlement consomme `TradeExecuted`). On a **révisé** vers **synchrone** :

- **trading appelle `settlement.scheduleSettlement(tradeId, bookingEntryId, tradeDate, assetClass)`** directement, dans sa transaction, en passant le `bookingEntryId` qu'il vient de créer.
- Donc **settlement NE dépend PAS de trading** (`{core, ledger}`) — les dépendances pointent vers le bas, pas de cycle.

Pourquoi : l'event-driven avait deux problèmes — (1) `TradeExecuted` ne porte pas le `bookingEntryId` (settlement ne pouvait pas construire l'instruction), (2) cycle `trading ↔ settlement` pour atteindre `SETTLED`. Le synchrone les supprime.

**Ce qui reste async** : le **batch** lui-même (le décalage T+2, déclenché par `@Scheduled`), et le fan-out d'events (`TradeSettled`, `SettlementBatchCompleted`…) vers les consumers de bord (UI, audit). Cf. la discussion sync/async.

## 3. Composants

- **`BusinessDayCalculator`** (`internal`) : `addBusinessDays(from, n, HolidayCalendar...)` + `isBusinessDay(...)`. Skip weekends + jours fériés des calendriers fournis (union pour un cross-currency). Phase 1 : appelé sans calendrier → weekends-only. **Test signature jqwik** : résultat toujours ouvré, après `from`, monotone, N jours ouvrés exacts dans l'intervalle.
- **`SettlementService`** (port) :
  - `scheduleSettlement(...)` → `SettlementInstruction` PENDING (valueDate = T+2 via le calculator), **idempotent** sur `tradeId` (UNIQUE en DB).
  - `runDueBatch(asOf)` → instructions PENDING avec `valueDate ≤ asOf` → chacune settle **dans sa propre TX `REQUIRES_NEW`** (`SettlementExecutor`), un échec n'arrête pas les autres → `SettlementBatch` COMPLETED/PARTIAL_FAILURE + events.
  - `findByTradeId(...)`.
- **`SettlementExecutor`** (`internal`) : `settle(id)` (→ `ledger.postSettlementEntry(bookingEntryId)` → SETTLED + `Fx/EquityTradeSettled`) et `markFailed(id, reason)` (→ FAILED + `SettlementFailed`), chacun `REQUIRES_NEW`. Bean séparé pour que le proxy transactionnel s'applique (pas de self-invocation).
- **`SettlementScheduler`** (`internal`, `@Scheduled` 6h UTC MON-FRI) → `runDueBatch(today)`. `@EnableScheduling` sur `LibraApplication`.
- **`AssetClass {FX, EQUITY}`** sur `SettlementInstruction` (+ colonne via **V3 Flyway**) : permet de publier le bon event sealed au batch sans relire le trade.

## 4. État du code

- `domain` : `SettlementInstruction` (aggregate, invariants croisés status + `assetClass`), `SettlementBatch` (invariant comptable), enums `SettlementStatus` / `BatchStatus` / `AssetClass`.
- `port` : `SettlementService` / `SettlementServiceImpl`. `internal` : `BusinessDayCalculator`, `SettlementExecutor`, `SettlementScheduler`.
- `events` : `TradeSettled` (sealed Fx/Equity), `SettlementFailed`, `SettlementBatchCompleted`.
- `persistence` + `repository` : entities/mappers + `findByTradeId`, `findByStatusAndValueDateLessThanEqual`. Tables `settlement_instructions` (UNIQUE trade_id) + `settlement_batches` dans V1, `asset_class` en V3.
- Tests : `BusinessDayCalculatorTest` (signature jqwik + exemples), `SettlementServiceIntegrationTest` (schedule idempotent → batch → ledger pending→final + isolation d'échec → PARTIAL_FAILURE).

## 5. Reste à faire (TODO)

- **Calendriers réels** : aujourd'hui weekends-only. Charger les `HolidayCalendar` par devise depuis `reference` (qui possède `holiday_calendars`/`holidays`) et les passer au calculator → settlement gagnerait une dépendance `reference`. Seed CSV par devise (2025-2026).
- **Retry policy** des FAILED (no-retry phase 1 ; reschedule = nouvelle instruction). Méthodes port `reschedule`/`forceSettle` + endpoints admin.
- **Order → SETTLED (phase 2)** : trading consommerait `TradeSettled` (async) pour clore le cycle d'ordre — gérer le cycle de modules (event partagé neutre).
- Métriques (`settlement.batch.duration`, `settlement.lag`, `settlement.failure.rate`), ADRs (021-024), topics Kafka.
- Désactiver le scheduler en test si besoin (aujourd'hui le cron 6h ne tombe pas pendant un run).

---

*À jour à l'issue de l'implémentation settlement (modèle synchrone). Dernier module restant : `trading` (orchestrateur, débloqué — appelle validation/pricing/ledger/settlement).*
