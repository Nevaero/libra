# CLAUDE.md — Libra

Guide d'orientation pour Claude Code reprenant le projet Libra. Toujours lire ce fichier en début de session.

## 1. Nature du projet

**Libra** est un broker multi-asset simplifié construit comme **side-project portfolio** ciblant les Swiss fintech (Swissquote, Lombard Odier, Pictet). Ce n'est PAS un produit de production : c'est un véhicule pour démontrer une maîtrise architecturale (DDD, CQRS, event-driven, modular monolith) lors d'entretiens.

**Différenciateur central : Physical Forex avec settlement T+2** (vs FX synthétique), aligné sur le concept-phare de Swissquote.

**Boussole pitch** (à garder en tête pour chaque décision) : *"FX et equity partagent un socle commun à 80% — ledger double-entry multi-asset, market data, moteur d'ordres générique. La spécificité Physical Forex (value dates, calendriers, T+2) est isolée dans `settlement`."*

## 2. Mode de travail : tutorat, pas implémentation autonome

Le projet est conduit en **mode tutorat architectural** avec Romain (utilisateur). C'est le mode par défaut sauf si l'utilisateur dit explicitement « implémente X ».

### Règles non-négociables du tutorat

- **Ne JAMAIS donner toutes les réponses d'un coup.** Romain le déteste, il l'a explicitement demandé.
- **Ping-pong court** > réponses massives. Une question à la fois si possible.
- **Découverte guidée par questions**, puis confirmer/corriger une fois qu'il propose.
- Quand Romain trouve la bonne réponse, **nommer le concept officiellement** (DDD, CQRS, event sourcing, value object, transactional outbox, ADR, etc.) — il doit pouvoir le réutiliser en entretien.
- Quand il se trompe, **donner un contre-exemple concret** plutôt qu'une correction abstraite.
- Pas de préambule mou ("Excellente question !"). Direct.
- Tableaux pour comparer des options, code Java conceptuel quand utile.
- Mentionner les **bonus pitch** quand un choix architectural devient un argument d'entretien.
- Profil de Romain : expert backend Java (6 ans), excellent en archi, **zéro connaissance métier finance**. L'élider sur le vocabulaire archi basique, le pousser sur les pièges métier.

### Numérotation des questions

Le compteur `Question N :` sert au **tutorat conception**. Les sessions ledger / reference / pricing ont basculé en **implémentation collaborative** sur demande explicite de Romain (« fais l'implém », « implémente X »), donc la numérotation est en pause. La reprendre (à partir de ~Q38) si on revient en conception pure.

## 3. Stack technique réelle (état actuel du repo)

⚠️ **Le code diverge des handoffs sur certains points**. Toujours faire confiance au code actuel, pas aux docs handoff sur ces points :

| Item | Handoff dit | Code actuel | Action |
|---|---|---|---|
| Build | Maven | **Gradle** (`build.gradle`) | Suivre Gradle |
| Java | 21 LTS | **Java 25** (toolchain) | Suivre Java 25 |
| Spring Boot | 3.4.x | **4.0.6** | Suivre 4.0.6 |
| Spring Modulith | 1.3.x | **2.0.6** | Suivre 2.0.6 |
| Postgres | 16 | **18.4** (compose + Testcontainers) | Flyway 12.6.2 (override : Boot manage 11.x qui ne reconnaît pas PG18) + `flyway-database-postgresql` |
| Lombok dans domaine | "Pas de Lombok" | **Records partout** (domaine = records ; persistence = POJO `@Data @Entity`) | Anti-Corruption Layer. Pas de `@Data` dans le domaine. |

Stack confirmée : PostgreSQL 18.4 + JPA + Flyway 12.6.2, Spring Kafka, Spring Modulith outbox (JPA + Kafka externalization, table `event_publication` matérialisée en Flyway car Modulith 2.x ne la crée plus), Actuator + Micrometer + Prometheus, springdoc-openapi, Spring Security, WebMVC + WebSocket. Tests : JUnit 5 (**Platform 6.0**), **AssertJ + jqwik** (property-based, déjà utilisés), Testcontainers (Postgres 18.4 + Kafka dans `TestcontainersConfiguration`). **`ModularityTests`** (`spring-modulith-starter-test`, `ApplicationModules.verify()`) garde les frontières de modules ; ArchUnit reste optionnel (la vérif Modulith couvre déjà cycles + dépendances + types exposés).

`compose.yaml` est **configuré** : `postgres:18.4` (host port **5433** pour éviter un conflit local) + `apache/kafka:4.3.0` (KRaft, single-node). `application.properties` pointe sur 5433.

## 4. Architecture : modules Spring Modulith

```
io.libra
├── LibraApplication
├── core                       # OPEN. Types partagés purs (Money, Asset, Currency, Security,
│                              #   CurrencyPair, Instrument) + SPI de résolution (AssetResolver,
│                              #   AssetRef, ReferenceResolution) + MoneyEntity/MoneyMapper. ZÉRO logique.
├── reference   ✅             # Security Master : persistance + cycle de vie des instruments
│                              #   (securities, currency_pairs, currencies), impl du SPI de résolution
│                              #   (batch IN-clause), events InstrumentListed/StatusChanged. Fermé, → core.
├── ledger      ✅             # double-entry, T+2 booking/settlement, BalanceProjector
├── pricing     ✅             # market data : QuoteService (upsert optimiste), adapters FIX/OANDA,
│                              #   bootstrap config-driven (YAML), port read getLatestQuote
├── customer    ✅             # CustomerService : onboarding + lifecycle réglementaire + events
├── validation  ✅             # pré-trade : Chain of Responsibility (5 règles), consomme customer/pricing/ledger
├── settlement  ✅             # T+2 synchrone : scheduleSettlement (idempotent) + batch @Scheduled
│                              #   (REQUIRES_NEW par instruction), BusinessDayCalculator. → {core, ledger}
├── trading     ✅             # orchestrateur ordres (phase 1) : submitOrder → idempotence → validation
│                              #   → exécution simulée → booking DvP → scheduleSettlement. → {core, util,
│                              #   ledger, pricing, validation, settlement} (en `:: port`/`domain`/`commands` selon besoin)
└── api          ✅            # REST : controllers thin (1/module front) → ports facades, DTOs
                              #   request/response, advice exceptions→HTTP, sécurité permit-all (placeholder). WS non créé.
```

Chaque module : `package-info.java` avec `@ApplicationModule` (+ `allowedDependencies`), sous-packages `port`/`events`/`internal`. `core` est OPEN, `util` OPEN. Chaque module fermé **publie des named interfaces fines** (`@NamedInterface` au niveau package) par rôle : `port`, `domain`, `commands` ; un consommateur déclare précisément les interfaces qu'il utilise (ex. `ledger :: port` + `ledger :: domain`, jamais `ledger :: commands`). `persistence`/`repository`/`internal`/`port.impl` restent encapsulés. `reference` reste fermé (`{"core","util"}`), l'impl du SPI est injectée via l'interface core (**Dependency Inversion**). Le tout est **vérifié par `ModularityTests` (`ApplicationModules.verify()`)** : pas de cycle, dépendances déclarées respectées, accès uniquement aux types exposés.

**Décision structurante** : le référentiel instruments a été extrait de `core` vers **`reference`** (un nouveau module). `core` ne porte que des **types**, jamais de logique/état. La résolution `(type, code, mic) → Asset` est un **SPI déclaré dans core, implémenté dans reference** (batch, élimine le N+1). Voir `docs/PRICING_HANDOFF.md` et les commits `reference`.

**Ordre d'implémentation** :
1. ✅ **Ledger** — implémenté + testé (`docs/LEDGER_HANDOFF.md`)
2. ✅ **Reference** (Security Master) — implémenté + testé
3. ✅ **Pricing** — implémenté + testé (`docs/PRICING_HANDOFF.md`) ; reste le transport réel (mock-feed, cf. handoff §6.1)
4. ✅ **Customer** — implémenté + testé (`docs/CUSTOMER_HANDOFF.md`)
5. ✅ **Validation** — implémenté + testé (`docs/VALIDATION_HANDOFF.md`)
6. ✅ **Settlement** — implémenté + testé, **modèle synchrone** (`docs/SETTLEMENT_HANDOFF.md`)
7. ✅ **Trading** — implémenté + testé, **phase 1** (`docs/TRADING_HANDOFF.md`) — dernier module métier

**Tous les modules métier sont implémentés, testés et vérifiés Modulith.** Reste l'`api` (REST/WebSocket) et les chantiers transverses (cf. §5).

## 5. État actuel du code

Schéma Flyway en place (`V1__schema.sql` + `V2` last-trade + `V3` settlement asset_class), tout compile et **70 tests passent** (unitaires + jqwik + intégration Testcontainers + `ModularityTests`), **0 warning**.

**Ledger** (`io.libra.ledger`) — **implémenté + testé** :
- domaine (records + invariant double-entry validé au compact constructor de `JournalEntry`), persistence `@Entity` + mappers MapStruct, repos, services par aggregate (`AccountManagementService`, `PostingService`, `ReadingService`, `MaintenanceService`), façade `LedgerService` (port), `BalanceProjector` interne (projection Balance dans la même TX, locking pessimiste), events via outbox, cycle T+2 booking→settlement. Test signature jqwik sur l'invariant.

**Reference / Security Master** (`io.libra.reference`) — **implémenté + testé** :
- entities/repos/mappers des instruments (securities, currency_pairs, currencies) **déplacés depuis core**, `ReferenceDataServiceImpl` (registration + state-machine lifecycle suspend/halt/delist), `ReferenceResolutionImpl` (impl du SPI `core.persistence.resolution`, résolution batch IN-clause), lookups par identité métier (`findSecurityByIsinAndMic`, `findPairByCodes`).

**Pricing** (`io.libra.pricing`) — **implémenté + testé** :
- `QuoteService` (ingest → upsert optimiste conditionnel sur `sequence`, publie `QuoteAdvanced` si advance), adapters `client.impl.{Fix,Oanda}PriceProviderClient` (un par source, conversion format brut → `PriceTick`), bootstrap `PricingSubscriptionBootstrap` (YAML `pricing-subscriptions.yml` → résolution via reference), port `PricingService` (`getLatestQuote`). Last trade equity supporté (COALESCE).

**Customer** (`io.libra.customer`) — **implémenté + testé** :
- `CustomerService` (port unique) : `onboard` (→ PENDING_KYC), state machine réglementaire (`activate` gated sur KYC, `suspend`/`reactivate`, `close`), `updateKycLevel`/`updateRiskProfile`, events via outbox. Domaine = records, persistence ACL.

**Validation** (`io.libra.validation`) — **implémenté + testé** :
- `ValidationService` (port) : portier pré-trade, Chain of Responsibility (5 règles : customer actif, KYC, instrument tradable, fonds suffisants, sanity prix limite), collect-all → `Approved`/`Rejected` + event `ValidationFailed`. Contexte construit depuis customer+ledger+pricing ; `LedgerService.findClientAccount` ajouté pour localiser le solde.

**Settlement** (`io.libra.settlement`) — **implémenté + testé** :
- **modèle synchrone** (révise le handoff event-driven) : trading appellera `scheduleSettlement(tradeId, bookingEntryId, tradeDate, assetClass)` dans sa TX → `settlement` ne dépend QUE de `{core, ledger}`, pas de cycle. `BusinessDayCalculator` (T+N, weekends + jours fériés, **test signature jqwik**), `SettlementService.runDueBatch` (batch matinal `@Scheduled`, chaque instruction settle en `REQUIRES_NEW` via `SettlementExecutor` → isolation d'échec → `SettlementBatch` COMPLETED/PARTIAL_FAILURE), `postSettlementEntry` du ledger pour la phase 2 (pending → final). `AssetClass` sur l'instruction + V3 Flyway. Seul morceau async = le décalage T+2 du batch.

**Trading** (`io.libra.trading`) — **implémenté + testé (phase 1)** :
- `TradingService.submitOrder` (port) : chemin de commande **synchrone**, une seule TX → idempotence sur `(clientId, idempotencyKey)`, `validation.validate` (portier), `ExecutionSimulator` interne (fill au quote courant : ask en BUY / bid en SELL, LIMIT marketable sinon no-fill), `TradeBooker` interne (**booking DvP à deux legs** sur comptes pending : leg base + leg quote = notional), puis `settlement.scheduleSettlement`. États terminaux `EXECUTED`/`REJECTED`/`CANCELLED` ; `SETTLED` viendra du batch (phase 2). Mono-leg, simulateur in-memory. `LedgerService.resolve{Client,Counterparty}Account` ajoutés (find-or-open idempotent) pour provisionner les 4 comptes du DvP.

**API** (`io.libra.api`) — **REST implémenté (sans sécurité)** :
- adaptateur inbound : un controller thin par module front (`Customer`, `Reference`, `Pricing`, `Ledger`, `Trading`) qui mappe des DTOs `dto/request` + `dto/response` vers/depuis le port facade. `ApiExceptionHandler` (`@RestControllerAdvice`) mappe `NoSuchElement`→404, `IllegalArgument`→400, `IllegalState`→409. `ApiSecurityConfig` = `SecurityFilterChain` permit-all + CSRF off (**placeholder**, à remplacer par une vraie auth). `TradingController` résout l'`Instrument` via reference avant `submitOrder`. Smoke test WebMvc (`@WebMvcTest`) sur les routes customer. Named interfaces ajoutées pour ce que `api` consomme (trading port/domain/commands, customer/reference commands, ledger enums account) — `ModularityTests` vert. **Settlement et Validation : pas de controller pour l'instant.**

**Plus de stub** : tous les modules métier portent de la logique testée.

**Ce qui reste globalement** : sécurité réelle sur l'`api` + WebSocket (streaming ordres/positions) ; métriques Micrometer custom ; le transport pricing réel (mock-feed Bun, cf. `docs/PRICING_HANDOFF.md` §6.1) ; calendriers de jours fériés réels pour settlement ; trading phase 2 (multi-leg, vrai venue, transition `Order → SETTLED` sur event `TradeSettled`).

## 6. Conventions transversales (à appliquer dans tout code écrit)

Ces conventions sont **load-bearing** pour le pitch Swissquote — ne pas dévier sans discussion :

- **UUIDv7** partout (pas v4) — ordre chronologique des indexes B-tree
- **`Instant` UTC** pour tous les timestamps, **jamais `LocalDateTime`**
- **Records Java** pour value objects / events / commands / DTOs
- **Sealed interfaces** pour hiérarchies fermées (déjà appliqué sur `Asset = Currency | Security`)
- **Montants : `BIGINT` en minor units** — jamais `double`, jamais `NUMERIC`, jamais `BigDecimal` dans une signature publique métier (toujours `Money`)
- **Deux colonnes DB** (`amount BIGINT` + `asset_code VARCHAR`) plutôt que type composite Postgres
- **`Math.addExact` / `subtractExact`** : fail-fast sur overflow (déjà fait dans `Money`)
- **`RoundingMode.UNNECESSARY`** dans `Money.of` (déjà fait) — force l'appelant à arrondir explicitement
- **Convention de signe ledger-centrique** : compte client = passif pour Libra → **CREDIT augmente** ce que le client possède (inverse de la convention bancaire client-side)
- **Settlement T+2 par batch journalier** (pas event scheduled), **appliqué uniformément à tous les trades** (FX physique ET equity). Two-phase booking : 1ère journal entry au booking T+0 sur des **comptes pending dédiés** (`*_pending_out`, `*_pending_in`), 2ème journal entry au settlement T+2 qui transfère pending → comptes finaux. Préserve l'immutabilité des postings ; `pendingDebits`/`pendingCredits` de la projection `Balance` sont dérivés de la position sur les comptes pending
- **Pattern matching exhaustif** Java 21+, pas de `default` artificiel sur les sealed
- **Foreign keys par UUID**, pas de `@ManyToOne` JPA lourd sauf besoin réel
- **Tous les events** publiés via outbox Spring Modulith (jamais directement vers Kafka)

## 7. Invariant fondamental du ledger (à protéger)

Pour toute `JournalEntry`, et pour chaque asset impliqué :

```
SUM(postings DEBIT,  asset=X) == SUM(postings CREDIT, asset=X)
```

**Doit être validé à la construction de la `JournalEntry`**, avant toute persistance. Une entry non balancée doit lever une exception. C'est l'invariant que jqwik (property-based testing) devra prouver — c'est **le test signature du projet**.

Aucun asset ne peut "apparaître"/"disparaître" : toute entrée/sortie passe par un compte de contrepartie (`MARKET_COUNTERPARTY`, `FX_COUNTERPARTY`, `NOSTRO`, `LIBRA_FEES`, etc.).

## 8. Documents de référence dans `docs/`

- **`docs/CLAUDE_HANDOFF.md`** : contexte global du tutorat, décisions stratégiques (Physical Forex, plan 6 semaines), méthode pédagogique, roadmap modules. **À relire en début de session de tutorat.**
- **`docs/LEDGER_HANDOFF.md`** : spec complète du module ledger (modèle conceptuel, schéma DB Flyway, API, events, tests, Definition of Done, 4 ADRs à écrire). **Référence canonique** quand on implémente le ledger ou qu'on doit garantir la cohérence d'un autre module avec lui.

Quand un module est conçu, produire un nouveau `docs/<MODULE>_HANDOFF.md` sur le même format.

## 9. Livrables attendus par module (Definition of Done type)

Pour chaque module : entités + invariants validés, schéma Flyway idempotent, port `Service` exposé via les named interfaces fines du module (`port`/`domain`/`commands`, package-level `@NamedInterface`), events publiés via outbox, tests unitaires + property-based + intégration Testcontainers + vérification Modulith (`ModularityTests`), métriques Micrometer, ADRs versionnées sous `docs/adr/`, README de module avec exemple déroulé.

## 10. Commandes utiles

```bash
./gradlew build           # compile + tests
./gradlew test            # tests uniquement
./gradlew bootRun         # démarre l'app (nécessite Postgres + Kafka, voir compose.yaml)
```

Le **configuration cache** Gradle est activé dans `gradle.properties` (`org.gradle.configuration-cache=true`, `org.gradle.parallel=true`), donc les builds réutilisent la configuration après le premier run.

## 11. Reprise de session — checklist Claude

1. Lire `docs/CLAUDE_HANDOFF.md`, `docs/LEDGER_HANDOFF.md`, `docs/PRICING_HANDOFF.md` si on n'est pas sûr du contexte.
2. Demander à Romain par quoi il veut commencer (par défaut : module **Customer**, prochain dans l'ordre).
3. Vérifier si on est en **conception (tutorat ping-pong)** ou en **implémentation collaborative** — Romain bascule explicitement.
4. En tutorat : ping-pong court, une question à la fois, nommer les concepts. En implémentation : garder la suite verte, committer par tâche.
5. Ne pas implémenter spontanément en mode tutorat — mais Romain demande souvent « fais l'implém » sur les modules récents.
