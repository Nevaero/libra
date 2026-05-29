# Libra

> A simplified multi-asset broker built as a portfolio project, centered on physical Forex with T+2 settlement.

**English** | [Français](#français)

---

## English

### What it is

Libra is a simplified multi-asset broker (FX and equities) built as a portfolio project. It is not a
production product. It is a vehicle to demonstrate architectural depth: domain-driven design, a
modular monolith with enforced boundaries, an event-driven core, and the core-banking mechanics a
real broker actually runs.

Its differentiator is **physical Forex with T+2 settlement, applied uniformly to equities**. Instead
of tracking prices synthetically, Libra delivers both sides of a trade at a value date through a
double-entry ledger, the way a real broker or custodian does. FX and equity trades share one
settlement engine because both are Delivery-versus-Payment: an exchange of value against value at a
settlement date.

### Architectural concepts

- **Modular monolith** with Spring Modulith. Each module publishes fine-grained named interfaces
  (`port`, `domain`, `commands`), and the boundaries are enforced at build time by
  `ApplicationModules.verify()`. One deployable, with the option to extract a module into a service
  later.
- **Domain-Driven Design**. Bounded contexts are the modules; aggregates and value objects model the
  domain; an anti-corruption layer separates pure domain records from JPA persistence.
- **Hexagonal (ports and adapters)**. Modules expose service ports; the inbound price feeds and the
  persistence layer are adapters; the domain stays free of infrastructure.
- **Double-entry ledger**. Immutable and append-only, with the per-asset balance invariant validated
  at construction (proven by a property-based test), and two-phase booking: pending accounts at T+0,
  final accounts at T+2.
- **Transactional outbox**. Every event is committed atomically with the state change that produced
  it, then relayed to Kafka at-least-once, so there are no lost or phantom events.
- **Synchronous command path, asynchronous fan-out**. The order-to-settlement flow runs in one ACID
  transaction for a consistent, authoritative result; events, the T+2 batch, and price ingestion are
  asynchronous.
- **Concurrency strategies chosen per workload**. Pessimistic locking on the ledger (conserved
  deltas that must each be applied), and a lock-free optimistic upsert on pricing (disposable
  snapshots where only the latest matters).
- **Conventions**. UUIDv7 identifiers, `Instant` UTC timestamps, and money as `BIGINT` minor units
  in a `Money` value object (no floating point, no `BigDecimal` in business signatures).
- **Testing**. JUnit 5, AssertJ, property-based tests with jqwik, and integration tests on real
  Postgres and Kafka via Testcontainers.

### Modules

`core` and `util` (shared kernel), `reference` (Security Master), `ledger` (double-entry, T+2
booking), `pricing` (market data), `customer` (regulatory lifecycle), `validation` (pre-trade gate),
`settlement` (T+2 scheduling and batch), `trading` (order orchestrator). A REST and WebSocket `api`
module is planned.

### Documentation

- **Architecture (C4 model)**: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). System Context,
  Container, and Component views, plus the command-path sequence and a map of the architectural
  principles to where they live in the code.
- **Architecture Decision Records**: [`docs/adr/`](docs/adr/). 21 records in MADR format, grouped by
  domain, explaining the reasoning behind every choice above.

### Tech stack

Java 25, Spring Boot 4, Spring Modulith 2, PostgreSQL 18, Apache Kafka (KRaft), Flyway, MapStruct,
Gradle.

### Build and run

```bash
./gradlew build     # compile and run the tests
./gradlew test      # tests only
./gradlew bootRun   # run the app (needs Postgres and Kafka, see compose.yaml)
```

### Status

Phase 1 backend is complete: every business module is implemented, tested, and boundary-verified.
The REST and WebSocket API and a frontend are planned.

---

## Français

### Ce que c'est

Libra est un broker multi-actifs simplifié (FX et actions) construit comme projet de portfolio. Ce
n'est pas un produit de production. C'est un support pour démontrer une maîtrise architecturale :
domain-driven design, un monolithe modulaire aux frontières vérifiées, un cœur événementiel, et les
mécaniques bancaires qu'un vrai broker opère réellement.

Son différenciateur est le **Forex physique avec règlement T+2, appliqué uniformément aux actions**.
Plutôt que de suivre les prix de façon synthétique, Libra livre les deux côtés d'une transaction à
une date de valeur, via un grand livre en partie double, comme le fait un vrai broker ou
dépositaire. Les transactions FX et actions partagent un seul moteur de règlement, car les deux sont
du Delivery-versus-Payment : un échange de valeur contre valeur à une date de règlement.

### Concepts architecturaux

- **Monolithe modulaire** avec Spring Modulith. Chaque module publie des named interfaces fines
  (`port`, `domain`, `commands`), et les frontières sont vérifiées à la compilation par
  `ApplicationModules.verify()`. Un seul déployable, avec la possibilité d'extraire un module en
  service plus tard.
- **Domain-Driven Design**. Les bounded contexts sont les modules ; aggregates et value objects
  modélisent le domaine ; une couche anti-corruption sépare les records du domaine de la persistance
  JPA.
- **Hexagonal (ports et adapters)**. Les modules exposent des ports de service ; les flux de prix
  entrants et la persistance sont des adapters ; le domaine reste libre de toute infrastructure.
- **Grand livre en partie double**. Immuable et append-only, avec l'invariant d'équilibre par actif
  validé à la construction (prouvé par un test property-based), et une comptabilisation en deux
  phases : comptes pending à T+0, comptes finaux à T+2.
- **Transactional outbox**. Chaque événement est commité atomiquement avec le changement d'état qui
  l'a produit, puis relayé vers Kafka en at-least-once, donc aucun événement perdu ni fantôme.
- **Chemin de commande synchrone, diffusion asynchrone**. Le flux ordre vers règlement s'exécute dans
  une seule transaction ACID pour un résultat cohérent et faisant autorité ; les événements, le batch
  T+2 et l'ingestion de prix sont asynchrones.
- **Stratégies de concurrence choisies par charge**. Verrouillage pessimiste sur le grand livre (des
  deltas conservés qui doivent chacun être appliqués), et un upsert optimiste sans verrou sur le
  pricing (des snapshots jetables où seul le dernier compte).
- **Conventions**. Identifiants UUIDv7, timestamps `Instant` UTC, et montants en `BIGINT` minor units
  dans un value object `Money` (pas de virgule flottante, pas de `BigDecimal` dans les signatures
  métier).
- **Tests**. JUnit 5, AssertJ, tests property-based avec jqwik, et tests d'intégration sur de vrais
  Postgres et Kafka via Testcontainers.

### Modules

`core` et `util` (noyau partagé), `reference` (Security Master), `ledger` (partie double,
comptabilisation T+2), `pricing` (données de marché), `customer` (cycle de vie réglementaire),
`validation` (portier pré-trade), `settlement` (planification et batch T+2), `trading`
(orchestrateur d'ordres). Un module `api` REST et WebSocket est prévu.

### Documentation

- **Architecture (modèle C4)** : [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Vues System Context,
  Container et Component, plus la séquence du chemin de commande et une carte reliant les principes
  architecturaux à leur emplacement dans le code.
- **Architecture Decision Records** : [`docs/adr/`](docs/adr/). 21 records au format MADR, groupés par
  domaine, expliquant le raisonnement derrière chaque choix ci-dessus.

### Stack technique

Java 25, Spring Boot 4, Spring Modulith 2, PostgreSQL 18, Apache Kafka (KRaft), Flyway, MapStruct,
Gradle.

### Compilation et exécution

```bash
./gradlew build     # compile et lance les tests
./gradlew test      # tests uniquement
./gradlew bootRun   # lance l'app (nécessite Postgres et Kafka, voir compose.yaml)
```

### État

Le backend de la phase 1 est complet : chaque module métier est implémenté, testé et vérifié au
niveau des frontières. L'API REST et WebSocket et un frontend sont prévus.
