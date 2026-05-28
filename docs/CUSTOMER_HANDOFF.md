# Libra — Customer Module Hand-off

> Document de hand-off pour Claude Code (ou tout autre assistant) chargé de reprendre la conception et l'implémentation du module `customer` de Libra.
>
> **État d'avancement** : data model + events conçus en tutorat ; logique métier, schéma DB, ports, ADRs, intégrations compliance externes **non encore tranchés**.

---

## 1. Contexte du projet

Rappel court — voir `CLAUDE.md`, `CLAUDE_HANDOFF.md` et les autres `*_HANDOFF.md` pour le contexte complet.

- Libra = broker multi-asset simplifié, side-project portfolio Swiss fintech.
- Stack : Java 25, Spring Boot 4.0.6, Spring Modulith 2.0.6, Gradle, PostgreSQL 16, Spring Kafka.
- Mode de travail : **tutorat ping-pong**, pas implémentation autonome.

---

## 2. Responsabilité du module Customer

Le module `customer` est le **bounded context réglementaire** de Libra. Il :

1. Porte l'**identité civile** des clients (PII : email, nom, date de naissance, pays de résidence).
2. Porte les **dimensions réglementaires** dérivées des standards MIFID II / LSFin-FinSA (catégorisation, KYC, profil de risque).
3. Gère le **cycle de vie** du client (onboarding → active → suspended/closed).
4. Publie les **events** de cycle de vie pour les modules en aval (notamment `validation`, qui dérive les capacités de trading depuis ces dimensions).

Il **ne** sait **rien** sur :

- Les écritures comptables (`ledger` — la relation est inversée : un `Account.ownerId` référence un `Customer.id` opaque)
- Les ordres et trades (`trading`)
- Les prix de marché (`pricing`)
- Les règles de validation pré-trade (`validation` — qui *consomme* customer)

`package-info.java` : `allowedDependencies = {"core"}`.

---

## 3. Modèle conceptuel — état actuel

### 3.1 Vocabulaire

| Terme | Définition |
|---|---|
| **Customer** | Aggregate root représentant un client. Record immuable ; un changement = nouveau record + event. |
| **PII** | Personally Identifiable Information (email, nom, date de naissance, etc.). Soumise au RGPD/nLPD. |
| **KycLevel** | Niveau de Know-Your-Customer obtenu (AMLA + Circulaire FINMA 2016/7 sur la vidéo-ID). |
| **RiskProfile** | Résultat du *suitability test* MIFID II / LSFin. Évalue la tolérance au risque, les connaissances, les objectifs. |
| **ClientCategory** | Catégorisation MIFID II — détermine le niveau de protection réglementaire. |
| **CustomerStatus** | Cycle de vie : `PENDING_KYC → ACTIVE → SUSPENDED → CLOSED`. |

### 3.2 Customer (aggregate root)

```java
public record Customer(
    UUID id,
    String email,
    String firstName,
    String lastName,
    LocalDate birthDate,
    String countryOfResidence,    // ISO 3166-1 alpha-2 (e.g. "CH")
    CustomerStatus status,
    KycLevel kycLevel,
    RiskProfile riskProfile,
    ClientCategory clientCategory,
    Instant onboardedAt,
    Instant closedAt              // non-null ssi status == CLOSED
) { ... }
```

**Invariants validés dans le compact constructor** :

- `requireNonNull` sur tous les champs sauf `closedAt`.
- `countryOfResidence` = exactement 2 caractères (basic ISO 3166-1 alpha-2 check ; pas de validation contre la liste ISO complète en phase 1).
- **Invariant croisé** : `closedAt non-null ⇔ status == CLOSED`. Empêche les états absurdes (CLOSED sans timestamp, ou ACTIVE avec un `closedAt` traînant).

### 3.3 Enums

```java
public enum CustomerStatus { PENDING_KYC, ACTIVE, SUSPENDED, CLOSED }
public enum KycLevel       { NONE, BASIC, ENHANCED, FULL }
public enum RiskProfile    { CONSERVATIVE, BALANCED, AGGRESSIVE }
public enum ClientCategory { RETAIL, PROFESSIONAL, ELIGIBLE_COUNTERPARTY }
```

Sémantique détaillée — voir les commentaires inline dans les fichiers source.

---

## 4. Events publiés

Tous via **Spring Modulith outbox** (jamais publish Kafka direct). Topics non encore décidés (voir §6).

### 4.1 CustomerOnboarded

```java
public record CustomerOnboarded(Customer customer, Instant occurredAt) { }
```

**Event-Carried State Transfer** : transporte le `Customer` entier. Consumers attendus : ledger (création des comptes initiaux), audit, projections UI.

### 4.2 CustomerStatusChanged

```java
public record CustomerStatusChanged(
    UUID customerId,
    CustomerStatus previousStatus,
    CustomerStatus newStatus,
    String reason,
    Instant occurredAt
) { }
```

**Event Notification** avec previous/current — cohérent avec le pattern `InstrumentStatusChanged` côté pricing. Le `reason` est texte libre (à formaliser en enum si besoin pour audit MIFID).

### 4.3 KycLevelChanged

```java
public record KycLevelChanged(UUID customerId, KycLevel previousLevel, KycLevel newLevel, Instant occurredAt) { }
```

Re-KYC, renouvellement périodique, escalade de tier.

### 4.4 RiskProfileChanged

```java
public record RiskProfileChanged(UUID customerId, RiskProfile previousProfile, RiskProfile newProfile, Instant occurredAt) { }
```

Résultat d'un nouveau questionnaire suitability.

### 4.5 Producers / consumers attendus

| Event | Publisher | Consumers attendus |
|---|---|---|
| `CustomerOnboarded` | customer (création) | ledger (ouverture comptes initiaux), audit, UI |
| `CustomerStatusChanged` | customer (transition statut) | validation (refus immédiat si SUSPENDED/CLOSED), audit |
| `KycLevelChanged` | customer (re-KYC) | validation (mise à jour des capacités), audit |
| `RiskProfileChanged` | customer (nouveau suitability test) | validation (mise à jour des capacités), audit |

---

## 5. Décisions architecturales actées

| Décision | Choix | Justification |
|---|---|---|
| Module séparé du ledger | Bounded context distinct | PII + KYC + MIFID vs comptabilité. Cycles de vie différents. Argument compliance : isoler les données personnelles pour audit + chiffrement at-rest (en phase 2) sans toucher au ledger. |
| Pas de référence aux comptes ledger | `Customer` ignore les `Account` ; c'est `Account.ownerId` qui référence `Customer.id` | Cohérent avec la décision sur trading : le ledger est l'authority of record sur les comptes. |
| Pas de permissions explicites sur `Customer` | Capacités dérivées par `validation` à partir de `(kycLevel, riskProfile, clientCategory)` | Évite duplication + désynchronisation. Si une règle évolue ("PROFESSIONAL peut trader des derivés"), on change un seul endroit. |
| PII inline | `email`, `firstName`, `lastName`, `birthDate` directement dans le record | Projet pédagogique. En prod, ces champs seraient extraits dans un PII vault chiffré (cf §6). |
| KYC = enum à 4 niveaux | `NONE / BASIC / ENHANCED / FULL` | Tiers MIFID II simplifiés. Démontre la conscience réglementaire sans rentrer dans les détails légaux. |
| Profil de risque = enum à 3 niveaux | `CONSERVATIVE / BALANCED / AGGRESSIVE` | Résultat du *suitability test* — input de validation pré-trade. |
| Catégorie client = enum MIFID II | `RETAIL / PROFESSIONAL / ELIGIBLE_COUNTERPARTY` | Détermine les protections applicables. |
| Date de naissance = `LocalDate` | Pas `Instant` | Pas un timestamp — pas d'heure ni de fuseau. La convention transverse "`Instant` UTC" concerne les *moments d'événements*, pas les dates civiles. |
| Pays de résidence = `String` ISO 3166-1 alpha-2 | Pas de VO `Country` en phase 1 | Standard international. Simplification pédagogique. |
| Record immuable | Un changement de statut = nouveau record + event | Cohérent event-sourcing-style avec le reste du domaine. |
| Style events | `Onboarded` = Event-Carried State Transfer ; `Changed` = Event Notification avec previous/current | Pattern hérité de pricing (`InstrumentStatusChanged`). |

---

## 6. Décisions ouvertes (à trancher en tutorat)

### 6.1 PII vault chiffré séparé

Pour la prod, extraire `email`, `firstName`, `lastName`, `birthDate` dans un module / table chiffré séparé. `Customer` ne porterait alors qu'un `piiVaultRef: UUID`. Permet le **tombstoning RGPD** (anonymiser les PII tout en conservant les métadonnées comptables — résolution du conflit AMLA 10 ans / RGPD right to erasure).

### 6.2 State machine `CustomerStatus`

Contrairement à `OrderStatus`, aucune méthode `canTransitionTo()` pour l'instant. À ajouter si validation l'exige (e.g. interdire `PENDING_KYC → CLOSED` direct sans passage par `ACTIVE`).

### 6.3 Renouvellement périodique

MIFID II / LSFin demande des révisions périodiques :
- KYC : annuel pour les profils ENHANCED/FULL, tous les 3-5 ans pour BASIC.
- Suitability : à la demande du client, ou après un événement de vie significatif.

À modéliser via un `lastReviewedAt: Instant` + un scheduler. Hors-scope phase 1.

### 6.4 PEP screening et sanctions list

Intégrations tierces nécessaires (Refinitiv World-Check, Dow Jones Risk, etc.). Doit être déclenché à l'onboarding et re-vérifié périodiquement. Hors-scope phase 1, mais impacte le data model (probablement un champ `pepStatus: PepStatus` + dates de dernière vérification).

### 6.5 Multi-citizenship / multi-residence (FATCA + CRS)

`countryOfResidence` est aujourd'hui un seul `String`. En réalité un client peut avoir :
- Plusieurs résidences fiscales (`Set<Country>`)
- Une ou plusieurs nationalités (pour FATCA — la *citoyenneté* US compte, pas la résidence)

Hors-scope phase 1 ; à acter quand on attaquera le module fiscalité (non prévu).

### 6.6 Reporting CRS / FATCA / MROS

Trois workflows distincts :
- **CRS** : reporting automatique des avoirs des résidents fiscaux étrangers.
- **FATCA** : reporting des US persons.
- **MROS** : signalement des soupçons de blanchiment.

Hors-scope phase 1. À mentionner explicitement dans la doc.

### 6.7 Topics Kafka

Topic candidat : `customer.lifecycle` (un seul topic pour tous les events, basse fréquence). Naming convention à fixer (`libra.customer.v1.lifecycle` ?). Clé de partitionnement : `customerId` (préserve l'ordre par client).

### 6.8 Schéma DB (Flyway)

Tables candidates :
- `customers` : un row par customer, colonnes correspondant aux champs du record.
- `customer_events` : si on veut un journal d'audit local en plus de l'outbox.

Index : UNIQUE sur `email`, index sur `(status, kyc_level)` pour les requêtes de revue.

### 6.9 Port `CustomerService`

À concevoir sur le modèle de `LedgerService` :

```java
public interface CustomerService {
    OnboardedCustomerResult onboard(OnboardCustomerCommand cmd);
    Optional<Customer> findById(UUID customerId);
    Optional<Customer> findByEmail(String email);
    void updateStatus(UUID customerId, CustomerStatus newStatus, String reason);
    void updateKycLevel(UUID customerId, KycLevel newLevel);
    void updateRiskProfile(UUID customerId, RiskProfile newProfile);
    void close(UUID customerId, String reason);
}
```

### 6.10 ADRs à écrire

- ADR-013 : Bounded context Customer séparé du Ledger (justification compliance)
- ADR-014 : PII inline phase 1, vault chiffré phase 2
- ADR-015 : Permissions dérivées (pas hardcoded sur Customer)
- ADR-016 : Conflit RGPD/AMLA résolu par tombstoning (à formaliser quand on attaque le vault)

### 6.11 Métriques Micrometer

- `customer.onboarded.count`
- `customer.status.transitions.count` (tagué par from/to)
- `customer.kyc.escalation.count` (tagué par from/to)
- `customer.active.gauge` (snapshot du nombre de clients actifs)

---

## 7. Conventions héritées (rappel transverse)

- **UUIDv7** partout, jamais v4.
- **`Instant` UTC** pour les timestamps, **`LocalDate`** pour les dates civiles (date de naissance).
- **Records** pour entités, events, commands, DTOs.
- **Pattern matching exhaustif** sur les enums (pas de `default` artificiel).
- **Pas de Lombok dans le domaine** — records purs.
- **Outbox Spring Modulith** pour tout event externalisé.

---

## 8. Hors-scope phase actuelle

- Logique d'onboarding (workflow KYC, vidéo-ID, vérification documents)
- PEP screening / sanctions list (Refinitiv, Dow Jones)
- Transaction monitoring AMLA (côté ledger ou settlement)
- MROS reporting workflow
- Renouvellement périodique KYC / suitability
- PII vault chiffré séparé
- CRS / FATCA reporting automatisé
- Tests (unitaires + intégration + ArchUnit)
- Schéma DB Flyway
- Endpoints REST `/customers`
- Topics Kafka et partitioning
- Métriques et observabilité
- ADRs
- README du module

---

## 9. État du code à ce point

```
io.libra.customer
├── package-info.java                  (@ApplicationModule, allowedDependencies={"core"})
├── entities/
│   ├── Customer.java                  (record + validations)
│   └── enums/
│       ├── CustomerStatus.java        (PENDING_KYC, ACTIVE, SUSPENDED, CLOSED)
│       ├── KycLevel.java              (NONE, BASIC, ENHANCED, FULL)
│       ├── RiskProfile.java           (CONSERVATIVE, BALANCED, AGGRESSIVE)
│       └── ClientCategory.java        (RETAIL, PROFESSIONAL, ELIGIBLE_COUNTERPARTY)
└── events/
    ├── CustomerOnboarded.java         (Event-Carried State Transfer)
    ├── CustomerStatusChanged.java     (Event Notification — previous/current/reason)
    ├── KycLevelChanged.java           (Event Notification — previous/current)
    └── RiskProfileChanged.java        (Event Notification — previous/current)
```

Aucune entité JPA mappée, aucun repository, aucun service, aucune migration Flyway, aucun event handler.

---

## 10. Reprise de session — prochaines étapes

1. Numérotation tutorat : prochaine question à incrémenter depuis la dernière posée.
2. Le data model `customer` est **complet** pour la phase 1.
3. Suites possibles :
    - Concevoir le module `settlement` (dernier en data model).
    - Implémenter (sortir du tutorat) : schéma DB Flyway, port `CustomerService`, premier endpoint REST d'onboarding.
    - Reprendre les modules existants sur leurs zones laissées ouvertes (PII vault, topics Kafka, etc.).
4. Ne **pas** implémenter spontanément — c'est un projet tutoré.

---

*Document à jour à l'issue de la phase data model + events du module Customer.*
