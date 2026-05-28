# Libra — Architecture Tutoring Hand-off

> Document de hand-off pour reprendre l'exercice de **conception architecturale tutorée** du projet Libra dans une nouvelle session Claude Code.
>
> Ce doc résume le contexte, ce qui a été conçu, et ce qui reste à faire — pour que la conversation puisse reprendre sans rejouer l'historique complet.

---

## 1. Contexte du projet Libra

### 1.1 Objectif

**Libra** est un broker multi-asset simplifié construit comme **side-project portfolio** pour :

- Démontrer une maîtrise architecturale (DDD, CQRS, event-driven, modular monolith) auprès des **Swiss fintech cibles** : Swissquote, Lombard Odier, Pictet
- Implémenter concrètement les concepts de *Designing Data-Intensive Applications* (Kleppmann)
- Modéliser le **Physical Forex avec settlement T+2** (différenciateur clé vs FX synthétique, et concept-phare de Swissquote)

L'objectif final : un projet qu'un non-technique de Swissquote / LO / Pictet puisse voir et se dire **"je veux ce type dans mon équipe"**.

### 1.2 Profil du développeur

- Expert backend Java (6 ans), très bon en architecture logicielle
- Aucune connaissance métier finance, mais connaît les mécanismes économiques de base
- En transition vers des rôles d'architecte logiciel
- Basé à Nyon (VD, Suisse)
- Disponibilité : 15-20h/semaine (entre deux missions)
- Préfère le **ping-pong court** aux longues réponses

### 1.3 Décisions stratégiques prises

| Question | Décision                                                                             |
|---|--------------------------------------------------------------------------------------|
| Quel angle pousser ? | **Physical Forex** (différenciateur Swissquote, vs equity trading qui est commodity) |
| Socle commun FX/equity ? | **Oui, ~80%** : un seul moteur générique, spécificités FX isolées dans `settlement`  |
| Plan de travail | **6 semaines** à ~15-20h/sem, ~100-120h total                                        |
| Nom du projet | **Libra** (la balance, symbole de double-entry, latin classique)                     |
| Packages Java | `io.libra.*`                                                                         |
| Architecture | **Single-module Gradle + Spring Modulith**                                            |
| Repo GitHub | À créer : `libra` (ou `libra-broker` si pris)                                        |

### 1.4 Stack technique fixée

| Couche | Choix |
|---|---|
| Langage | Java 21 LTS |
| Framework | Spring Boot 3.4.x |
| Architecture | Spring Modulith 1.3.x (modular monolith) |
| Persistance | PostgreSQL 16 + Spring Data JPA + Flyway |
| Messaging | Spring Kafka + Kafka (KRaft mode, single-broker local Docker) |
| Build | Maven (single-module) |
| Tests | JUnit 5, AssertJ, Testcontainers, jqwik (property-based), ArchUnit |
| Observabilité | Actuator + Micrometer + Prometheus |
| Doc API | springdoc-openapi |
| Frontend (plus tard) | Angular 21 + WebSocket |

### 1.5 Plan global sur 6 semaines

| Semaine | Focus |
|---|---|
| 1 | Fondations ledger multi-devises + invariants double-entry |
| 2 | Module Accounts + Portfolio statique + Angular shell |
| 3 | Kafka + market data FX streaming + Angular temps réel |
| 4 | **Cœur Physical Forex avec settlement T+2** (différenciateur) |
| 5 | Orders equity + CQRS + projections analytiques |
| 6 | Fault tolerance, replay, polish, vidéo de démo |

---

## 2. Méthode de tutorat à reprendre

> ⚠️ **Important pour Claude Code reprenant le tutorat** : la méthode pédagogique est aussi importante que le contenu.

### 2.1 Posture du tuteur

- **Ne JAMAIS donner toutes les réponses d'un coup.** Romain le déteste et l'a explicitement demandé.
- **Préférer le ping-pong court** à des réponses massives. Une question à la fois si possible.
- **Faire découvrir les concepts par questions guidées**, puis confirmer/corriger.
- Quand Romain trouve la bonne réponse, **nommer le concept officiellement** (DDD, CQRS, event sourcing, value object, transactional outbox, etc.) pour qu'il puisse en parler en entretien.
- Quand il se trompe, **donner un contre-exemple concret** plutôt qu'une correction abstraite.
- Romain a un fond solide en archi, donc l'élider sur le vocabulaire trop basique mais le pousser sur les pièges métier (finance).

### 2.2 Style de réponse attendu

- **Direct, sans préambules vagues** ("Excellente question !")
- Tableaux et structures pour comparer des options
- Code Java quand utile, mais conceptuel d'abord
- Mentionner les **bonus pitch** quand un choix architectural se traduit en argument d'entretien
- Ne pas hésiter à recadrer si Romain pose une question hors-sujet ou si une décision passée mérite d'être remise en cause

### 2.3 Format des questions

Numéroter les questions (`Question 38 :`, `Question 39 :`...) en continuant le compteur. La dernière question posée était la **Question 37** (sur le module Ledger, déjà répondue).

---

## 3. Ce qui a été conçu — État actuel

### 3.1 Domaine et vocabulaire métier établis

Romain a découvert et appris par questions guidées :

- **Bounded contexts** : 6 modules identifiés (`customer`, `ledger`, `trading`, `pricing`, `validation`, `settlement`)
- **Cycle de vie d'un trade FX** : booking (T+0) → pending settlement → settled (T+2)
- **Distinction comptable / disponible** : `bookBalance` vs `availableBalance`
- **Double-entry avec comptes de contrepartie** (market, fees, nostro) — rien n'apparaît/disparaît
- **CQRS** : événements = source de vérité, soldes = projection
- **Event sourcing** : projection rebuildable depuis l'event log
- **Distinction commande synchrone / event asynchrone**
- **Push vs pull** pour le market data (push)
- **Multi-asset unifié** : actions = "devise" d'un compte position (insight TigerBeetle-style)
- **Value object vs entity** : Currency = VO, Security = Entity (cycle de vie via splits, dividendes, statut)
- **Transactional Outbox** via Spring Modulith
- **Convention BIGINT minor units** (à la Stripe/TigerBeetle)

### 3.2 Architecture des 6 modules

```
┌───────────────────────────────────────────────────┐
│              io.libra (single Maven module)        │
├───────────────────────────────────────────────────┤
│                                                    │
│  customer    pricing                               │
│     │           │                                  │
│     ▼           ▼                                  │
│  validation ◄───┐                                  │
│     ▲           │                                  │
│     │           │                                  │
│  trading ──────►├──► ledger ──► settlement         │
│     │           │      ▲             │             │
│     └───────────┘      └─────────────┘             │
│                                                    │
└───────────────────────────────────────────────────┘
                      │
                  Kafka events
                      │
                ┌─────┴──────┐
                │  Angular   │
                └────────────┘
```

### 3.3 Events principaux identifiés

| Event | Publisher | Consumers | Moment |
|---|---|---|---|
| `OrderSubmitted` | trading | (validation via sync command) | clic client |
| `OrderAccepted` | trading | settlement, ledger | post-validation OK |
| `FxTradeBooked` | trading | ledger, settlement | étape FX |
| `EquityOrderExecuted` | trading | ledger | étape equity |
| `FxTradeSettled` | settlement | ledger | J+2 batch matinal |
| `OrderRejected` | trading (sync) | UI directe | si validation KO |
| `PriceTick` | pricing | trading, validation, projections | continu |
| `LedgerEntryPosted` | ledger | settlement, projections, audit | sur chaque écriture |

### 3.4 Module Ledger — conception complète

**Status : ✅ Hand-off prêt pour implémentation** (voir `LEDGER_HANDOFF.md`).

Inclut :
- Modèle conceptuel complet (`Asset`/`Currency`/`Security`, `Money`, `Account`, `JournalEntry`, `Posting`, `Balance`)
- Invariants double-entry validés à la construction
- Schéma DB Postgres (Flyway)
- API du module (`LedgerService` port)
- Events publiés (`JournalEntryPosted`, `AccountOpened`, `AccountStatusChanged`)
- Stratégie de projection synchrone (phase 1) avec rebuild possible
- Outbox via Spring Modulith
- Job de réconciliation nocturne
- Tests requis : unitaires + property-based (jqwik) + intégration (Testcontainers) + ArchUnit
- 4 ADRs à rédiger (modular monolith, BIGINT minor units, sync projection, outbox)
- Definition of Done

### 3.5 Décisions architecturales transverses prises

- **UUIDv7** partout (pas v4) pour l'ordre chronologique des indexes B-tree
- **`Instant` UTC** pour tous les timestamps, jamais `LocalDateTime`
- **Records Java 21** pour value objects, events, commands
- **Sealed interfaces** pour les hiérarchies fermées (`Asset = Currency | Security`)
- **`BIGINT` en minor units** pour tous les montants (jamais `double`, jamais `NUMERIC`)
- **Deux colonnes DB** (`amount BIGINT` + `asset_code VARCHAR`) plutôt que type composite Postgres
- **`Math.addExact` / `subtractExact`** pour fail-fast sur overflow
- **Settlement T+2 par batch journalier** (pas event scheduled) — fidèle au métier
- **Convention de signe ledger-centrique** : compte client = passif pour Libra, CREDIT augmente

---

## 4. Ce qui reste à faire — Roadmap des modules

### 4.1 Ordre d'implémentation recommandé

L'ordre suivant respecte les dépendances :

1. ✅ **Ledger** — hand-off prêt
2. ⏭️ **Pricing** — PROCHAIN MODULE À CONCEVOIR
3. **Customer** — léger, à concevoir après pricing
4. **Validation** — dépend de pricing + ledger + customer
5. **Trading** — dépend de tout le reste
6. **Settlement** — dépend du ledger, écoute trading

### 4.2 Module Pricing — à concevoir maintenant

**Responsabilités à clarifier avec Romain :**

- Référentiel d'instruments (FX pairs + securities) — ou délégué à un autre module ?
- Ingestion / simulation de prix (FX rates + equity quotes)
- Publication d'events `PriceTick` sur Kafka
- API de lookup synchrone (pour validation pré-trade qui a besoin d'un prix snapshot)
- Stockage historique des prix (pour charts, audit, replay)

**Points d'architecture à creuser avec Romain (questions tutorat) :**

1. **Modèle de données du prix**
    - Bid/ask vs single price ? Tu vas vouloir bid/ask (spread = source de revenus broker).
    - Source du prix : exchange ? agrégateur ? simulateur ?
    - Quoting convention FX : EUR/CHF = 0.93 signifie quoi exactement ? (1 EUR = 0.93 CHF). Direction de la paire.

2. **Push vs pull (déjà tranché : push)**
    - Comment exposer un endpoint sync pour validation qui doit lire un prix maintenant ?
    - Cache mémoire du dernier prix par instrument ? Topic compacté Kafka ?

3. **Partitionnement Kafka**
    - Une partition par instrument ? Par classe d'asset ? Round-robin ?
    - Implications sur l'ordering et la parallélisation des consumers ?

4. **Simulateur de prix réaliste**
    - Random walk avec mean reversion autour de taux réels
    - Tick rate configurable
    - Genère bid + ask avec un spread

5. **Distinction Currency Pair vs Security**
    - Une "FX pair" est un instrument tradable (EUR/CHF) distinct des deux currencies sous-jacentes
    - Faut-il une entité `CurrencyPair` ? Ou est-ce dérivable ?
    - Cross rates : trader EUR/JPY quand on n'a que EUR/USD et USD/JPY

6. **Historique et stockage**
    - Time-series DB (TimescaleDB extension Postgres) ou table Postgres standard ?
    - Compaction / aggregation pour les charts longue durée ?
    - Phase 1 vs évolutions futures

7. **Cycle de vie des prix**
    - Que faire quand un marché est fermé (heures non-ouvrées) ?
    - Calendriers d'instruments (FX 24/5, equity SIX 9h-17h30, NASDAQ 15h30-22h CET, etc.)

**Approche tutorat recommandée :**

Commencer par : *"Qu'est-ce qu'un prix, conceptuellement ? Si je te dis 'le prix de l'EUR/CHF est 0.93', qu'est-ce que ça contient comme information cachée ?"*

Faire émerger : bid/ask, spread, timestamp, source, paire orientée, etc.

Puis enchaîner : *"Si tu dois afficher ce prix dans l'UI Angular en temps réel, et que validation pré-trade doit aussi le consulter au moment d'un ordre, quels patterns d'accès tu vois ?"*

### 4.3 Module Customer — à concevoir après pricing

**Responsabilités à clarifier :**

- Identité, KYC, profil investisseur (MIFID II side)
- Statut compte client (actif, suspendu, fermé)
- Permissions de trading (qui peut trader quoi : equity oui, options non, FX leveraged non, etc.)
- Référence aux comptes ledger (un customer → N accounts)

**Points à creuser :**

- Niveau de KYC modélisé (sans aller en prod, juste assez pour démontrer la conscience du cadre réglementaire)
- Profil de risque (conservateur, équilibré, agressif) influence validation
- Pourquoi Customer est-il un module séparé et non absorbé dans Ledger ?

### 4.4 Module Validation — à concevoir après customer

**Responsabilités :**

- Pré-trade checks : solde dispo, limites de position, statut security, permissions client, heures de marché
- Sync command appelée par trading avant exécution
- Renvoie OK ou raison de rejet structurée

**Points à creuser :**

- Règles configurables ou hardcoded ?
- Chaîne de validateurs (Chain of Responsibility) ?
- Idempotence : si validation est appelée 2x pour le même ordre, même résultat ?
- Read-only vs side effects (validation ne doit JAMAIS muter le ledger, juste lire)

### 4.5 Module Trading — à concevoir après validation

**Responsabilités :**

- Réception des ordres clients (`SubmitOrderCommand`)
- Orchestration : appel validation → simulation d'exécution → publication events
- Types d'ordres : MARKET, LIMIT (suffisant pour démo)
- Order book simplifié (pas un vrai matching engine — un market order s'exécute au mid, un limit attend son niveau)
- Gestion de la conversion multi-devise (acheter AAPL avec un compte CHF = FX puis equity buy)

**Points à creuser :**

- Trading est-il un seul module ou faut-il séparer Order Management (OMS) et Execution (EMS) ?
- Qui orchestre le "achat multi-devise" — Trading ou un nouveau module ?
- Comment garantir l'idempotence d'un ordre (client retry sur timeout) ?

### 4.6 Module Settlement — dernier

**Responsabilités :**

- Cycle de vie T+2 des trades FX
- Job batch matinal (9h CET, accéléré en dev)
- Calcul des value dates (jours ouvrés + calendriers de devises)
- Communication "virtuelle" avec contreparties (mockée)
- Génération des events `FxTradeSettled` qui déclenchent les écritures finales dans le ledger

**Points à creuser :**

- `BusinessDayCalculator` avec calendrier CH + zone Euro + US
- Cut-off times (trade après 17h CET → T+3)
- Que faire si un settlement échoue (contrepartie indisponible) ?
- Cross rates et triangulation

---

## 5. Artefacts existants

| Fichier | Statut | Description |
|---|---|---|
| `LEDGER_HANDOFF.md` | ✅ Livré | Spec complète du module ledger pour implémentation |
| `TUTORING_HANDOFF.md` | ✅ Ce doc | Pour reprendre le tutorat sur les prochains modules |
| Repo GitHub | ❌ À créer | `libra` ou `libra-broker` |
| Pom Maven parent | ❌ À créer | Avec Spring Boot 3.4.x BOM |
| ADRs | ❌ À écrire | Au fil de l'implémentation |
| Diagramme d'archi global | ❌ À faire | Mermaid/PlantUML/Excalidraw |

---

## 6. Instructions pour Claude Code reprenant la conversation

### 6.1 Lire d'abord

1. Ce document (`TUTORING_HANDOFF.md`) — contexte global
2. `LEDGER_HANDOFF.md` — référence pour la cohérence des choix sur le ledger

### 6.2 Demander à Romain par quoi il veut commencer

**Question d'ouverture suggérée :**

> *"Salut Romain, on reprend la conception architecturale de Libra. Le module Ledger est conçu (hand-off livré). Tu veux qu'on attaque Pricing maintenant comme prévu, ou tu préfères un autre angle ?"*

### 6.3 Reprendre la méthode

- Continuer la numérotation des questions à partir de **Question 38**
- Ping-pong court, pas de réponses massives
- Découverte guidée plutôt que dump d'infos
- Nommer les concepts DDD/archi quand Romain les redécouvre
- Garder en tête le **pitch Swissquote** comme boussole : chaque choix d'archi doit pouvoir se justifier dans un entretien

### 6.4 Livrable attendu par module

Pour chaque module conçu, produire un `<MODULE>_HANDOFF.md` sur le même format que `LEDGER_HANDOFF.md` :

- Contexte et responsabilités
- Modèle conceptuel
- API exposée
- Events publiés/consommés
- Schéma DB
- Décisions architecturales
- Tests requis
- Definition of Done
- Hors-scope explicite

### 6.5 Cohérence transverse à préserver

- `Money` est défini dans `core`, partagé par tous les modules
- Tous les events sont publiés via outbox Spring Modulith
- Tous les timestamps en `Instant` UTC
- Tous les montants en `BIGINT` minor units
- UUIDv7 partout
- Records pour value objects / events / commands
- Sealed interfaces pour hiérarchies fermées

---

## 7. Pitch Swissquote — angle à garder en tête

Le projet doit pouvoir se raconter en 30 secondes :

> *"J'ai construit un broker simplifié pour comprendre comment Swissquote fonctionne en profondeur. Mon insight principal a été de réaliser que FX et equity partagent un socle commun à 80% — un ledger double-entry multi-asset, un pipeline de market data, un moteur d'ordres générique. La spécificité du Physical Forex, c'est le settlement T+2 avec gestion des value dates et des calendriers de devises, et c'est exactement ça que j'ai isolé dans un module dédié. La stack est Spring Boot + Kafka + Angular, modular monolith via Spring Modulith, event sourcing pour le ledger, CQRS pour les projections, transactional outbox pour la propagation des events. Tout est testé jusqu'à l'invariant double-entry prouvé par property-based testing."*

Chaque décision architecturale doit pouvoir se défendre dans cette histoire.

---

*Document à jour au moment du hand-off après conception du module Ledger. À actualiser au fil de la conception des modules suivants.*