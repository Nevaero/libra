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

Continuer le compteur `Question N :` à travers les sessions. **Dernière question posée : Question 37** (sur le module Ledger). La prochaine est donc **Question 38**, sur le module Pricing.

## 3. Stack technique réelle (état actuel du repo)

⚠️ **Le code diverge des handoffs sur certains points**. Toujours faire confiance au code actuel, pas aux docs handoff sur ces points :

| Item | Handoff dit | Code actuel | Action |
|---|---|---|---|
| Build | Maven | **Gradle** (`build.gradle`) | Suivre Gradle |
| Java | 21 LTS | **Java 25** (toolchain) | Suivre Java 25 |
| Spring Boot | 3.4.x | **4.0.6** | Suivre 4.0.6 |
| Spring Modulith | 1.3.x | **2.0.6** | Suivre 2.0.6 |
| Lombok dans domaine | "Pas de Lombok" | **Records partout** (Security/Account/JournalEntry/Posting convertis) | Aligné sur la décision originelle. Pas de réintroduction de `@Data` dans le domaine. |

Stack confirmée et stable : PostgreSQL 16 + JPA + Flyway, Spring Kafka, Spring Modulith outbox (JPA + Kafka externalization), Actuator + Micrometer + Prometheus, springdoc-openapi, Spring Security, WebMVC + WebSocket. Tests : JUnit 5, Testcontainers (Kafka conf déjà en place dans `TestcontainersConfiguration`). À ajouter quand nécessaire : AssertJ, jqwik (property-based), ArchUnit.

`compose.yaml` est vide pour l'instant (`services: { }`) — pas de Postgres/Kafka en local encore configuré.

## 4. Architecture cible : 6 modules Spring Modulith

```
io.libra
├── LibraApplication
├── core                       # VO partagés (Money, Asset, Currency, Security)
├── ledger      ← conçu, stubs en cours
├── trading
├── pricing     ← PROCHAIN module à concevoir
├── validation
├── customer
├── settlement
└── api                        # REST + WebSocket (non créé)
```

Chaque module : `package-info.java` avec `@ApplicationModule`, sous-packages `api` (ports exposés), `events` (events publiés), `internal` (impl hidden). Le module `core` est en `Type.OPEN` (partagé).

**Ordre d'implémentation prévu** :
1. ✅ Ledger — handoff écrit (`docs/LEDGER_HANDOFF.md`), entités stubbées
2. ⏭️ **Pricing** — à concevoir en tutorat maintenant
3. Customer
4. Validation
5. Trading
6. Settlement

## 5. État actuel du code

**Ce qui existe** (mais stubbé, sans logique métier ni invariants) :

- `io.libra.LibraApplication` — entrypoint Spring Boot
- `io.libra.core.entities` : `Asset` (sealed), `Currency` (record), `Security` (record, implémente `Asset` et `Instrument`), `Instrument` (sealed, permits `Security`/`CurrencyPair`), `CurrencyPair` (record), `Money` (record, `of/plus/minus/toDecimal` implémentés avec `Math.addExact` et `RoundingMode.UNNECESSARY`)
- `io.libra.core.entities.enums` : `SecurityType`, `SecurityStatus`, `CurrencyPairStatus`, `InstrumentStatus` (sealed, permits `SecurityStatus`/`CurrencyPairStatus`)
- `io.libra.ledger.domain` : `Account`, `Balance`, `JournalEntry`, `Posting` — **records, validations minimales** (formule available=book−pendingDebits+pendingCredits sur `Balance`, defensive `List.copyOf` sur `JournalEntry.postings`)
- `io.libra.ledger.domain.enums` : `PostingType`, `AccountStatus`, `AccountType`, `EntryStatus`, `EntryType`
- `package-info.java` pour `core` (OPEN) et `ledger` (allowedDependencies = {"core"})
- `TestcontainersConfiguration` (Kafka container), `LibraApplicationTests` (contextLoads vide)

**Ce qui n'existe PAS encore** (mais est documenté dans `docs/LEDGER_HANDOFF.md` comme cible) :

- Aucun schéma Flyway (`src/main/resources/db/migration/` n'existe pas)
- Aucune entité JPA mappée (les classes ne portent pas `@Entity`)
- Aucun repository / service / port
- Aucun event publié (`JournalEntryPosted`, `AccountOpened`, etc.)
- Aucun test métier (juste `contextLoads`)
- Aucun ADR (`docs/adr/` n'existe pas)
- Aucune métrique Micrometer custom
- Job de réconciliation, endpoint admin rebuild balance : non créés
- `compose.yaml` vide → pas de Postgres/Kafka local

**Petits défauts à signaler à Romain en passant** (ne pas corriger silencieusement) :

- *(rien à signaler actuellement — la divergence Lombok et la typo `journalEntreyId` ont été résolues)*

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

Pour chaque module : entités + invariants validés, schéma Flyway idempotent, port `Service` exposé via `@org.springframework.modulith.NamedInterface`, events publiés via outbox, tests unitaires + property-based + intégration Testcontainers + ArchUnit, métriques Micrometer, ADRs versionnées sous `docs/adr/`, README de module avec exemple déroulé.

## 10. Commandes utiles

```bash
./gradlew build           # compile + tests
./gradlew test            # tests uniquement
./gradlew bootRun         # démarre l'app (nécessite Postgres + Kafka — compose.yaml encore vide)
```

## 11. Reprise de session — checklist Claude

1. Lire `docs/CLAUDE_HANDOFF.md` et `docs/LEDGER_HANDOFF.md` si on n'est pas sûr du contexte.
2. Demander à Romain par quoi il veut commencer (par défaut : conception du module **Pricing**, Question 38).
3. Reprendre le **ping-pong court**, pas de réponses massives.
4. Numéroter les questions à partir de la dernière posée.
5. Ne JAMAIS implémenter spontanément sans demande explicite — c'est un projet d'apprentissage tutoré.
