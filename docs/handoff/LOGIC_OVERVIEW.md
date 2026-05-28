# Libra — Vue d'ensemble de la logique à implémenter

> Document compagnon des `*_HANDOFF.md` par module.
> Les handoffs détaillent **le data model et les décisions architecturales** de chaque module ; ce document donne **la vue transverse de la logique restante à implémenter** pour passer du data model à une application Libra fonctionnelle.
>
> Pour chaque composant : référence vers la section §6 du handoff concerné pour les détails.

---

## 1. Vue exécutive

Après la phase data model, la liste exhaustive des **types de composants à produire** pour amener Libra à un POC fonctionnel :

| Couche | Type de composant | Modules concernés |
|---|---|---|
| **Persistance** | Schéma DB Flyway + repositories JPA | les 6 modules métier (sauf `validation` qui est read-only) |
| **Ports** | Interface `*Service` exposée via `@NamedInterface` Spring Modulith | les 6 modules métier |
| **Logique métier** | Implémentations des services + calculators + règles | tous |
| **Event handlers** | Consommateurs Spring Modulith des events cross-modules | ledger, settlement, pricing |
| **Outbox externalization** | Configuration Kafka + sérialiseurs polymorphiques (sealed) | tous |
| **Schedulers** | Spring `@Scheduled` batch jobs | settlement (batch T+2), ledger (réconciliation nocturne) |
| **API layer** | REST controllers + WebSocket | module `api/` (non créé) |
| **Tests** | Unitaires + property-based (jqwik) + intégration (Testcontainers) + ArchUnit | tous |
| **Observabilité** | Métriques Micrometer + endpoints Actuator + Prometheus | tous |
| **ADRs** | Architecture Decision Records | tous (~24 ADRs identifiés cumulés sur les handoffs) |
| **Documentation** | README par module + diagrammes Mermaid | tous |

---

## 2. Ordre d'implémentation recommandé

L'ordre suit (a) les **dépendances** entre modules, (b) la **valeur pitch** des tests signatures qu'on veut écrire en priorité, (c) le **plan 6 semaines** de `CLAUDE_HANDOFF.md` §1.5.

### Semaine 1 — Fondations Ledger

1. Schéma Flyway ledger (currencies, securities, accounts, balances, journal_entries, postings — cf `LEDGER_HANDOFF.md` §5.3).
2. Repositories Spring Data JDBC (records-friendly).
3. `LedgerService` port + impl avec validation invariant double-entry à la construction.
4. Tests : unitaires `MoneyTest` / `JournalEntryTest` + **property-based jqwik sur l'invariant double-entry** (le *test signature* du projet — argument pitch direct).
5. Outbox `JournalEntryPosted` configuré.

**Definition of Done semaine 1** : on peut poser une JournalEntry de phase IMMEDIATE (deposit cash) et lire la balance résultante. jqwik tourne 1000+ itérations sans casser l'invariant.

### Semaine 2 — Customer + Pricing référentiel + Validation

1. Schéma Flyway customer (`customers`) + `CustomerService` port + impl.
2. Schéma Flyway pricing référentiel (`instruments`, `currency_pairs`, `securities`, `providers`) + `PricingService` lookup-only.
3. Seed des principales devises (CHF, USD, EUR, JPY, GBP) et pairs (EUR/USD, USD/CHF, EUR/CHF, USD/JPY, GBP/USD).
4. Implémentation des 5 `ValidationRule` côté validation (`BalanceCheckRule`, `CustomerActiveCheckRule`, `KycCheckRule`, `InstrumentStatusCheckRule`, `LimitPriceSanityCheckRule`).
5. Tests : unitaires par règle + property-based sur la composition.

**Definition of Done semaine 2** : on peut onboarder un customer, créer ses comptes ledger initiaux via event handler `CustomerOnboarded`, et valider une intention de trade synthétique.

### Semaine 3 — Market data streaming

1. Schéma Flyway pricing (`latest_quotes`, optionnel `price_ticks_history`).
2. Simulateur de prix in-memory (random walk + mean reversion autour de taux réels). Tick configurable par instrument.
3. Event handler `PriceTick` → update `LatestQuote` projection.
4. Outbox `PriceTick` externalisé vers Kafka (topic à fixer — cf `PRICING_HANDOFF.md` §6.1).
5. WebSocket endpoint côté `api/` pour streaming des PriceTick vers frontend (frontend pas encore créé).

**Definition of Done semaine 3** : on voit les ticks couler sur Kafka, la projection `LatestQuote` se met à jour, le WebSocket diffuse vers un client de test.

### Semaine 4 — Cœur Physical Forex T+2 (différenciateur)

1. **`BusinessDayCalculator`** — **le test signature de ce module**. Property-based jqwik sur 10 000 dates aléatoires : jamais un weekend, jamais un jour férié, monotone. Argument pitch direct.
2. Seed des `HolidayCalendar` Flyway (CH, US, EUR-TARGET2 pour 2025-2026).
3. Schéma Flyway settlement (`settlement_instructions`, `settlement_batches`, `holiday_calendars`, `holidays`).
4. Event handler `FxTradeExecuted` → crée `SettlementInstruction(PENDING, T+2)`.
5. `SettlementScheduler` Spring `@Scheduled` matinal.
6. `LedgerService.postSettlementEntry(bookingEntryId)` — dérive automatiquement les postings SETTLEMENT depuis l'entry BOOKING (transfert pending → finaux).
7. Tests d'intégration end-to-end avec horloge accélérée Testcontainers.

**Definition of Done semaine 4** : un trade FX peut être booké à T+0, settled à T+2, balance pending devient balance finale. Le flow tient la charge de 1000+ trades sur un batch.

### Semaine 5 — Trading equity + CQRS

1. Schéma Flyway trading (`parent_orders`, `orders`, `trades`) + UNIQUE `(client_id, idempotency_key)`.
2. `TradingService` port + impl : `submitOrder`, `cancelOrder`, idempotence DB-level.
3. Décomposition `ParentOrder` → child Orders (logique multi-leg cross-currency).
4. Simulateur d'exécution in-memory (MARKET au mid, LIMIT déclenché par tick).
5. Outbox de tous les events trading + sérialiseurs polymorphiques (Jackson `@JsonTypeInfo` pour les sealed `Order` / `TradeExecuted`).
6. Premier endpoint REST `/orders` côté `api/`.
7. Projections de portfolio (CQRS — read-model dérivé du ledger + trading).

**Definition of Done semaine 5** : un client peut passer un ordre equity avec un compte en devise différente (cross-currency), les deux legs sont décomposés, exécutés, et settlés à T+2.

### Semaine 6 — Fault tolerance, observabilité, polish, démo

1. Job de réconciliation ledger (`@Scheduled` 3h du matin) avec endpoint admin rebuild balance.
2. Tests ArchUnit complets : `ApplicationModules.verify()` + interdictions transverses.
3. Métriques Micrometer + dashboards Prometheus / Grafana (toutes les métriques identifiées dans les §6 des handoffs).
4. ADRs écrites (~24 cumulées sur tous les handoffs).
5. README par module + diagrammes Mermaid de l'architecture globale.
6. Vidéo de démo : le flow complet onboard → order → settle visualisé en temps réel sur l'UI Angular (si frontend implémenté).
7. Replay de l'event log pour démontrer la propriété event-sourcing.

**Definition of Done semaine 6** : POC démontrable end-to-end + argumentaire pitch articulable en 30 secondes.

---

## 3. Détail par module — composants à produire

### 3.1 Ledger

Référence : `LEDGER_HANDOFF.md` §5–6.

| Composant | Type | Priorité | Notes |
|---|---|---|---|
| Schéma Flyway (6 tables) | Migration | 🔴 | Inclut comptes `pending=true` |
| Repositories Spring Data JDBC | Persistance | 🔴 | Records-friendly, `@Id` sur composant |
| `LedgerService.postJournalEntry(cmd)` | Logique | 🔴 | Valide invariant double-entry par asset à la construction |
| `LedgerService.postSettlementEntry(bookingEntryId)` | Logique | 🟠 | Dérive postings SETTLEMENT depuis BOOKING |
| Update synchrone des `Balance` (FOR UPDATE) | Logique | 🔴 | Cf §5.4 du handoff |
| Outbox `JournalEntryPosted`, `AccountOpened`, `AccountStatusChanged` | Spring Modulith | 🔴 | JPA + Kafka externalization |
| Job de réconciliation nocturne | `@Scheduled` | 🟡 | Recalcule depuis postings, alerte sur divergence |
| Endpoint admin rebuild balance | REST | 🟡 | `POST /admin/ledger/rebuild-balance/{accountId}` |
| Test property-based jqwik sur invariant double-entry | Test | 🔴 | **Test signature du projet** |
| Tests intégration Testcontainers | Test | 🔴 | |
| Métriques (`ledger.postings.count`, etc.) | Observabilité | 🟡 | |
| ADRs 001-004 + 012 | Doc | 🟡 | Modular monolith / BIGINT / sync projection / outbox / T+2 uniforme |

### 3.2 Pricing

Référence : `PRICING_HANDOFF.md` §6.

| Composant | Type | Priorité | Notes |
|---|---|---|---|
| Schéma Flyway (instruments, currency_pairs, providers, latest_quotes) | Migration | 🔴 | |
| Seed devises + paires principales | Migration | 🔴 | CHF, USD, EUR, JPY, GBP + paires |
| `PricingService` port (`getLatestQuote`, `findInstrument`, `listActiveInstruments`) | Logique | 🔴 | |
| Simulateur de prix (random walk + mean reversion) | Logique | 🔴 | In-memory, configurable tick rate |
| Event handler `PriceTick` → upsert `LatestQuote` | Logique | 🔴 | Avec `sequence` pour ignorer out-of-order |
| Outbox `PriceTick`, `InstrumentListed`, `InstrumentStatusChanged` + sérialiseurs polymorphiques | Spring Modulith | 🔴 | `Instrument` sealed → Jackson `@JsonTypeInfo` |
| Endpoint REST + WebSocket pour streaming | API | 🟠 | Côté module `api/` |
| Calendrier marchés (heures d'ouverture) — décision ouverte | Logique | 🟡 | Soit pricing soit module `calendar` séparé |
| Tests | Test | 🔴 | |
| Métriques (`pricing.tick.latency`, etc.) | Observabilité | 🟡 | |
| ADRs 005-008 | Doc | 🟡 | |

### 3.3 Customer

Référence : `CUSTOMER_HANDOFF.md` §6.

| Composant | Type | Priorité | Notes |
|---|---|---|---|
| Schéma Flyway (`customers`) + index `UNIQUE(email)` | Migration | 🔴 | |
| `CustomerService` port + impl | Logique | 🔴 | |
| Endpoint REST `/customers` onboarding | API | 🟠 | |
| Event handler `CustomerOnboarded` côté ledger → ouvre comptes initiaux | Logique | 🟠 | Cross-module : ledger consume |
| State machine `CustomerStatus.canTransitionTo()` | Logique | 🟡 | Si besoin de transitions contraintes |
| Tests | Test | 🟡 | |
| Métriques (`customer.onboarded.count`, etc.) | Observabilité | 🟡 | |
| ADRs 013-016 | Doc | 🟡 | |

**Hors-scope POC** : PII vault chiffré, PEP screening, sanctions list, MROS workflow, renouvellement KYC périodique, CRS/FATCA reporting.

### 3.4 Validation

Référence : `VALIDATION_HANDOFF.md` §6.

| Composant | Type | Priorité | Notes |
|---|---|---|---|
| `ValidationService` port + impl | Logique | 🔴 | Construit `ValidationContext`, itère sur les règles, agrège les reasons |
| Logique de `BalanceCheckRule` | Logique | 🔴 | Formule d'exposition par type d'ordre |
| Logique de `CustomerActiveCheckRule` | Logique | 🔴 | Switch exhaustif sur `CustomerStatus` |
| Logique de `KycCheckRule` | Logique | 🔴 | Matrice `(kycLevel, clientCategory, instrument type)` |
| Logique de `InstrumentStatusCheckRule` | Logique | 🔴 | Lookup pricing |
| Logique de `LimitPriceSanityCheckRule` | Logique | 🟠 | Seuils ±X% configurables |
| Outbox `ValidationFailed` | Spring Modulith | 🟠 | Audit MIFID II |
| Tests property-based sur composition des règles | Test | 🔴 | |
| Tests unitaires par règle | Test | 🔴 | Avec `ValidationContext` fabriqués |
| Métriques (`validation.failures.count` par code) | Observabilité | 🟡 | |
| ADRs 017-020 | Doc | 🟡 | |

**Hors-scope POC** : `ClientLimit` (limites de position), calendrier marché complet, configuration injectée des règles.

### 3.5 Trading

Référence : `TRADING_HANDOFF.md` §6.

| Composant | Type | Priorité | Notes |
|---|---|---|---|
| Schéma Flyway (`parent_orders`, `orders`, `trades`) | Migration | 🔴 | UNIQUE `(client_id, idempotency_key)` aux deux niveaux |
| `TradingService` port (`submitOrder`, `cancelOrder`, etc.) | Logique | 🔴 | |
| Décomposition `ParentOrder` → child Orders cross-currency | Logique | 🔴 | Le différenciateur multi-leg |
| Simulateur d'exécution (MARKET au mid, LIMIT déclenché par tick) | Logique | 🔴 | In-memory, pas un vrai matching engine |
| Idempotence DB-level (race-condition-proof) | Logique | 🔴 | |
| Event handlers (subscribe `PriceTick` pour LIMIT trigger) | Logique | 🟠 | |
| Outbox de tous les events trading + sérialiseurs sealed | Spring Modulith | 🔴 | `Order` et `TradeExecuted` polymorphiques |
| Endpoint REST `/orders`, `/parent-orders` | API | 🟠 | |
| State machine `OrderStatus.canTransitionTo()` déjà codée ✅ | Logique | ✅ | |
| Tests | Test | 🔴 | |
| Métriques (`trading.orders.submitted.count`, etc.) | Observabilité | 🟡 | |
| ADRs 009-011 | Doc | 🟡 | |

### 3.6 Settlement

Référence : `SETTLEMENT_HANDOFF.md` §6.

| Composant | Type | Priorité | Notes |
|---|---|---|---|
| **`BusinessDayCalculator`** | Logique | 🔴 | **Test signature pitch** — property-based jqwik. Joint plusieurs HolidayCalendar (USD/JPY = US ∪ JP). |
| Seed des `HolidayCalendar` (CH, US, EUR-TARGET2 pour 2025-2026) | Migration | 🔴 | CSV par devise |
| Schéma Flyway (`settlement_instructions`, `settlement_batches`, `holiday_calendars`, `holidays`) | Migration | 🔴 | UNIQUE `(trade_id)` pour idempotence |
| `SettlementService` port + impl | Logique | 🔴 | |
| Event handler `FxTradeExecuted` / `EquityTradeExecuted` → crée `SettlementInstruction` | Logique | 🔴 | Idempotent via UNIQUE constraint |
| `SettlementScheduler` Spring `@Scheduled` matinal | Logique | 🔴 | Cron à fixer (9h CET ?). Transaction par instruction. |
| Endpoints admin (reschedule, force-settle) | API | 🟡 | |
| Retry policy pour FAILED | Logique | 🟡 | Décision ouverte |
| Outbox `TradeSettled` sealed + `SettlementFailed` + `SettlementBatchCompleted` | Spring Modulith | 🔴 | |
| Tests property-based `BusinessDayCalculator` | Test | 🔴 | **Test signature** |
| Tests intégration end-to-end avec horloge accélérée | Test | 🔴 | |
| Métriques (`settlement.lag`, `settlement.batch.duration`, etc.) | Observabilité | 🔴 | `settlement.lag` doit rester ~0 |
| ADRs 021-024 | Doc | 🟡 | |

**Légende priorité** : 🔴 indispensable POC | 🟠 important polish | 🟡 nice-to-have | ✅ déjà fait

---

## 3bis. Phase 5 — entités non migrées vers `@PersistenceEntity`

L'annotation processor `@PersistenceEntity` (`persistence-processor/`) génère `<Entity>Row` + `<Entity>Mapper` à la compilation pour tout record domaine annoté. Phase 5.2 a appliqué l'annotation sur 12 entités ; 3 cas restent volontairement hors scope car ils nécessitent une évolution du processor ou un refactor structurel :

| Entité | Raison du skip | Évolution requise |
|---|---|---|
| `pricing.entities.LatestQuote` | Clé primaire **composite** (`instrumentId`, `tenor`). Le processor actuel suppose une PK simple via `idField` (default `"id"`) ; aucun support de PK multi-colonnes via `@Embedded` Spring Data ou clé composite explicite. | Refactor du processor : accepter `idField` comme `String[]` et générer une PK composite côté Row (annotation Spring Data appropriée + signature de repository `<X, CompositeKey>`). |
| `trading.entities.Order` + `MarketOrder` + `LimitOrder` | `Order` est une **sealed interface** avec deux records permits. Le processor lit les composants d'un *record* concret ; il n'a pas de stratégie de single-table inheritance ni de discriminator column. | Étendre le processor : supporter `@PersistenceEntity` sur une sealed interface en générant un Row commun avec discriminator + colonnes union des permits, ou utiliser le pattern table-per-subtype (cf. `instruments` + `securities` / `currency_pairs`). |
| `trading.entities.ParentOrder` | Composant `List<UUID> childOrderIds`. Le processor accepte `List<X>` uniquement si `X` est un autre `@PersistenceEntity` (génère `@MappedCollection` + injection ChildMapper). `List<UUID>` exigerait soit une table fille dédiée, soit un type collection custom. | Ajouter un cas `List<simple>` au TypeAnalyzer générant une table de jointure simple `parent_id + value` + `@MappedCollection`. |

Ces 3 entités garderont leur représentation domaine pure pour la phase 5 et seront migrées dans une itération ultérieure du processor (Phase 6+).

---

## 4. Cross-cutting concerns

### 4.1 Spring Modulith outbox + Kafka externalization

Configuration `application.yml` (déjà esquissée dans `LEDGER_HANDOFF.md` §5.5) :

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

**Sérialisation polymorphique** : les events sealed (`Order` dans `OrderSubmitted`, `Instrument` dans `PriceTick`, `TradeExecuted`, `TradeSettled`) nécessitent une configuration Jackson `@JsonTypeInfo` ou un sérialiseur custom. À résoudre **transversalement** dans une seule classe de configuration, pas par event.

### 4.2 Schéma Flyway global

Migration ordering proposé :
- `V1__currencies_and_securities.sql` (ledger référentiel)
- `V2__instruments_and_pairs.sql` (pricing référentiel)
- `V3__customers.sql`
- `V4__accounts_and_balances.sql` (ledger)
- `V5__journal_entries_and_postings.sql` (ledger)
- `V6__holiday_calendars_and_holidays.sql` (settlement, calendar dans core)
- `V7__settlement_instructions_and_batches.sql`
- `V8__parent_orders_orders_trades.sql` (trading)
- `V9__providers_and_latest_quotes.sql` (pricing)
- `V100__seed_currencies.sql`, `V101__seed_pairs.sql`, `V102__seed_holiday_calendars.sql` (seeds)

À acter au moment d'écrire la première migration.

### 4.3 Tests ArchUnit + Spring Modulith

```java
@Test
void verifyModularStructure() {
    ApplicationModules modules = ApplicationModules.of(LibraApplication.class);
    modules.verify();
}
```

Vérifie : aucune dépendance vers `internal/` d'un autre module, respect des `allowedDependencies` déclarés, exposition explicite via `@NamedInterface`.

Tests additionnels via ArchUnit pur :
- Tous les events sont des `record` immuables.
- Aucun `@Data` Lombok dans le domaine (interdit).
- Tous les timestamps métier sont `Instant`, pas `LocalDateTime`.
- Aucun usage de `double`/`float` dans les classes du domaine.

### 4.4 Observabilité

- Actuator endpoints exposés (`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`).
- Micrometer registry Prometheus.
- Métriques cumulées des 6 modules (cf §6.x de chaque handoff).
- Dashboards Grafana à produire (hors-scope phase 1, mais documenter les métriques pour quand on l'attaquera).

### 4.5 API layer (module `api/`)

Module non encore créé. Quand il le sera :
- REST controllers exposant les ports des modules métier.
- WebSocket endpoint `/ws/prices` pour streaming des `PriceTick`.
- Spring Security minimal (auth par token pour les démos).
- springdoc-openapi pour documentation Swagger.

### 4.6 Frontend Angular (phase ultérieure)

- Angular 21 + WebSocket pour le temps réel.
- Pages : onboarding, dashboard balance, order entry, historical trades.
- **Hors-scope phase 1** — à attaquer après la vidéo de démo.

---

## 5. Tests signatures à mettre en avant pour le pitch

Ces tests sont la **preuve de maîtrise architecturale** qu'on veut articuler en entretien Swiss fintech. Chaque module a son test signature :

| Module | Test signature | Argument pitch |
|---|---|---|
| `ledger` | **jqwik property-based double-entry** | *"sur n'importe quelle séquence aléatoire de N journal entries, l'invariant `sum(debits) = sum(credits) par asset` tient et la projection balance reste cohérente avec la somme des postings"* |
| `settlement` | **jqwik property-based `BusinessDayCalculator`** | *"sur 10 000 dates de départ aléatoires, T+N retombe toujours sur un jour ouvré bancaire, jamais un weekend, jamais un jour férié, et le calcul est monotone"* |
| `validation` | **property-based composition de règles** | *"la composition des règles est déterministe, parallélisable, et exhaustive — toutes les raisons de rejet sont retournées, pas seulement la première"* |
| `trading` | **idempotence DB-level race-condition-proof** | *"sous N requêtes concurrentes avec la même idempotency key, exactement un Order est créé, démontré via Testcontainers + stress test"* |
| `pricing` | **out-of-order tick rejection** | *"un tick avec `sequence < current` est ignoré ; la projection LatestQuote ne régresse jamais"* |
| `customer` | **invariant croisé status / closedAt** | *"un Customer CLOSED a toujours un closedAt non-null ; aucun état illégal n'est représentable"* |
| Cross-cutting | **ArchUnit + `ApplicationModules.verify()`** | *"le modular monolith est compile-time-enforced — toute dépendance interdite casse le build"* |

---

## 6. Definition of Done globale (POC livrable)

Le POC Libra est considéré comme **livrable et démontrable** quand :

- [ ] Les 6 modules ont leur schéma Flyway versionné + seeds (CHF/USD/EUR/JPY/GBP + paires + jours fériés 2025-2026 CH/US/EUR).
- [ ] Tous les ports `*Service` sont exposés via `@NamedInterface`.
- [ ] Le flow end-to-end fonctionne : onboarding customer → submit `ParentOrder` cross-currency → validation OK → décomposition en 2 children → exécution → `TradeExecuted` x2 → booking ledger T+0 sur comptes pending → batch `SettlementScheduler` matinal T+2 → `postSettlementEntry` ledger → balance finale settled.
- [ ] Tests verts : unitaires + property-based sur les tests signatures + Testcontainers intégration + ArchUnit.
- [ ] Outbox Spring Modulith opérationnel, events visibles dans Kafka (Kafka UI ou `kcat`).
- [ ] Endpoints Actuator + métriques Prometheus.
- [ ] ADRs (24 cumulées) versionnées dans `docs/adr/`.
- [ ] README par module avec exemple déroulé.
- [ ] Diagramme d'architecture global (Mermaid).
- [ ] Vidéo de démo de 2-3 minutes du flow complet.

**Hors-scope POC mais à mentionner en entretien comme évolutions naturelles** :
- Frontend Angular complet.
- PII vault chiffré séparé (RGPD compliance prod).
- PEP screening / sanctions list (Refinitiv, Dow Jones).
- MROS reporting workflow.
- TimescaleDB pour l'historique de prix.
- Multi-provider et règles d'agrégation des quotes.
- Suitability test périodique automatisé.
- TimeInForce avancés (GTC, IOC, FOK), partial fills, options.
- Reporting MIFID II RTS 27/28 best execution.

---

*Document compagnon des `*_HANDOFF.md` par module. Lecture conseillée après les handoffs pour avoir la vue d'ensemble de ce qui reste à coder. À actualiser au fil de l'implémentation.*
