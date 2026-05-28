# Libra — Validation Module Hand-off

> Document de hand-off pour Claude Code (ou tout autre assistant) chargé de reprendre la conception et l'implémentation du module `validation` de Libra.
>
> **État d'avancement** : data model + structure des règles (sealed Chain of Responsibility) conçus en tutorat ; **logique métier de chaque règle = stubs `Optional.empty()`**, à implémenter en phase suivante.

---

## 1. Contexte du projet

Rappel court — voir `CLAUDE.md`, `CLAUDE_HANDOFF.md` et les autres `*_HANDOFF.md`.

- Libra = broker multi-asset simplifié, side-project portfolio Swiss fintech.
- Stack : Java 25, Spring Boot 4.0.6, Spring Modulith 2.0.6, Gradle, PostgreSQL 16, Spring Kafka.
- Mode de travail : **tutorat ping-pong**.

---

## 2. Responsabilité du module Validation

Le module `validation` est le **moteur de pré-trade checks** de Libra. Il :

1. Reçoit une **command synchrone** depuis `trading` (`validate(ValidationRequest)`).
2. Construit un **`ValidationContext`** enrichi en lookupant les entités nécessaires (`Customer`, `Balance`, `LatestQuote`).
3. Applique une **chaîne de règles** (`ValidationRule` sealed) au contexte.
4. Renvoie un **`ValidationResult`** sealed (`Approved` ou `Rejected` avec liste de raisons).
5. Publie un event **`ValidationFailed`** sur chaque rejet (traçabilité MIFID II).

Spécificité architecturale : **read-only strict**. Validation ne mute jamais le ledger, ne crée jamais d'entités persistantes. C'est de la logique pure consommant l'état des autres modules.

`package-info.java` : `allowedDependencies = {"core", "ledger", "pricing", "customer"}`.

---

## 3. Modèle conceptuel — état actuel

### 3.1 Vocabulaire

| Terme | Définition |
|---|---|
| **ValidationRequest** | DTO neutre porté par `trading` au moment de l'appel sync. Pas de couplage à `Order` (préserve la direction trading → validation). |
| **ValidationContext** | Snapshot enrichi : request + entités fetchées (customer, balance, latestQuote). Construit une fois, partagé par toutes les règles. |
| **ValidationRule** | Sealed interface — une règle est un record concret implémentant `validate(context)`. Composition par Chain of Responsibility. |
| **ValidationResult** | Sealed : `Approved` ou `Rejected`. ADT propre — le type *sait* si la validation a réussi. |
| **ValidationFailureReason** | Couple `(code: ValidationFailureCode, detail: String)`. Code typé pour audit, detail human-readable. |

### 3.2 ValidationRequest (DTO neutre)

```java
public record ValidationRequest(
    UUID orderId,
    UUID clientId,
    Instrument instrument,
    Side side,
    Money quantity,
    Optional<Long> limitPriceMinorUnits   // empty si MARKET, present si LIMIT
) { }
```

**Choix archi clé** : pas de champ `Order` (qui appartient au module trading). Trading construit cette requête neutre à partir de son `Order` au moment de l'appel — ainsi validation ne dépend pas de trading.

### 3.3 ValidationContext

```java
public record ValidationContext(
    ValidationRequest request,
    Customer customer,
    Balance sourceBalance,
    Optional<LatestQuote> latestQuote     // empty si market closed ou pas de quote récent
) { }
```

Construit **une seule fois** par le service avant d'itérer sur les règles. Évite des fetches répétés depuis chaque règle.

### 3.4 ValidationResult (sealed ADT)

```java
public sealed interface ValidationResult permits Approved, Rejected {
    UUID orderId();
    Instant validatedAt();
}

public record Approved(UUID orderId, Instant validatedAt) implements ValidationResult { }

public record Rejected(
    UUID orderId,
    Instant validatedAt,
    List<ValidationFailureReason> reasons
) implements ValidationResult { ... }
```

**Pattern *Make Illegal States Unrepresentable*** : un `Approved` *ne peut pas* porter de reasons ; un `Rejected` *doit* en porter au moins une (validé dans le compact constructor). Le compilateur garantit l'exhaustivité via pattern matching côté trading.

### 3.5 ValidationFailureReason + code

```java
public record ValidationFailureReason(ValidationFailureCode code, String detail) { }

public enum ValidationFailureCode {
    INSUFFICIENT_FUNDS,         // availableBalance < exposition de l'ordre
    CUSTOMER_NOT_ACTIVE,        // status != ACTIVE
    KYC_INSUFFICIENT,           // kycLevel trop bas pour l'instrument visé
    INSTRUMENT_NOT_TRADABLE,    // SUSPENDED, HALTED, DELISTED, DEACTIVATED
    LIMIT_PRICE_OUT_OF_BOUNDS,  // limitPrice irréaliste vs latestQuote
    MARKET_CLOSED               // heures de marché fermées pour l'instrument
}
```

`code` typé pour audit régulateur et switch exhaustif ; `detail` libre pour contexte humain (e.g. *"available 7065 USD < required 10000 USD"*).

### 3.6 ValidationRule (sealed Chain of Responsibility)

```java
public sealed interface ValidationRule
    permits BalanceCheckRule,
            CustomerActiveCheckRule,
            KycCheckRule,
            InstrumentStatusCheckRule,
            LimitPriceSanityCheckRule
{
    Optional<ValidationFailureReason> validate(ValidationContext context);
}
```

Chaque règle est un **record stateless** (potentiellement avec config injectée par constructeur en phase logique). Le service applique toutes les règles, collecte les `Optional.of(reason)`, et :
- Aucun rejet → `Approved`.
- ≥ 1 rejet → `Rejected(orderId, validatedAt, reasons)`.

**Patterns saillants** :

- **Sealed + records** = ADT exhaustif. Ajouter une règle = ajouter un record + l'ajouter à `permits`. Le compilateur le force.
- **Chain of Responsibility** sans short-circuit : *toutes* les règles sont appliquées, pas seulement la première qui rejette. Permet à l'UI d'afficher *toutes* les raisons de rejet d'un coup (meilleure UX et meilleur audit).
- **Règles pures fonctionnelles** : `(context) -> Optional<reason>`. Testables isolément, parallélisables si besoin.

### 3.7 Règles concrètes (phase 1 = stubs)

| Règle | Responsabilité (commentée dans le code) |
|---|---|
| `BalanceCheckRule` | `availableBalance >= exposition de l'ordre`. Calcul de l'exposition dépend du type : MARKET → `qty × midPrice` ; LIMIT → `qty × limitPrice` ; SELL → check position en asset cible. |
| `CustomerActiveCheckRule` | `customer.status == ACTIVE`. Bloque PENDING_KYC, SUSPENDED, CLOSED. |
| `KycCheckRule` | `kycLevel` suffisant pour l'instrument. Mapping documenté : `NONE` bloque tout ; `BASIC` cash + FX spot petit ticket ; `ENHANCED` + equity ; `FULL` + leveraged. Tient compte de `clientCategory`. |
| `InstrumentStatusCheckRule` | Instrument dans un statut tradable : `Security.ACTIVE` uniquement ; `CurrencyPair.ACTIVE` uniquement. Lookup courant du status depuis pricing (pas snapshot embarquée). |
| `LimitPriceSanityCheckRule` | Pour LIMIT uniquement : `limitPrice` dans une fourchette raisonnable du `latestQuote` (e.g. ±90% du mid → fat-finger error). Pas applicable aux MARKET. |

Chaque record retourne actuellement `Optional.empty()` (stub). Logique métier à implémenter en phase suivante.

---

## 4. Events publiés

### 4.1 ValidationFailed

```java
public record ValidationFailed(
    UUID orderId,
    UUID clientId,
    List<ValidationFailureReason> reasons,
    Instant occurredAt
) { ... }
```

Publié **uniquement sur rejet**. Justification MIFID II : traçabilité réglementaire des décisions de validation, y compris quand la décision aboutit à un rejet *avant même* qu'un Order soit persisté côté trading. Sans event séparé, ces décisions n'apparaîtraient nulle part dans l'audit log.

Distinct de `OrderRejected` (côté trading) qui n'est publié que si un Order a été créé en amont.

### 4.2 Pas d'event sur succès

Choix d'archi délibéré : pas de `ValidationApproved` ou `ValidationCompleted`. Le résultat sync `Approved` suffit ; trading publie ensuite `OrderAccepted`. Émettre un event par validation = event spam pour bénéfice nul (audit MIFID exige la traçabilité des *décisions*, pas des success cases).

---

## 5. Décisions architecturales actées

| Décision | Choix | Justification |
|---|---|---|
| Module read-only | Validation ne mute jamais le ledger | Cohésion fonctionnelle : *valider* est distinct de *faire*. Permet la testabilité et l'idempotence triviale. |
| Sync command, pas event-driven | `trading` appelle un port `ValidationService.validate(...)` synchronement | Évite un round-trip et une state machine "PENDING_VALIDATION → VALIDATED" inutile. La réponse est nécessaire pour décider de la suite. |
| Sealed `ValidationResult` | `Approved | Rejected` (ADT) | Make Illegal States Unrepresentable — un `Approved` ne peut structurellement pas porter de reasons. |
| Sealed `ValidationRule` + records concrets | Chain of Responsibility typée | Plus testable, plus DDD, exhaustif au compilateur. Romain a tranché Option 2 (vs hardcoded `if-else`). |
| Pas de short-circuit | Toutes les règles sont appliquées, pas seulement la première qui rejette | UI peut afficher toutes les raisons en une fois ; meilleur audit. |
| `ValidationRequest` = DTO neutre | Pas de champ `Order` typé | Préserve la direction du couplage : trading → validation, pas l'inverse. |
| `ValidationContext` snapshot enrichi | Construit en amont par le service, partagé par toutes les règles | Fetch unique des entités externes, règles purement fonctionnelles. |
| Refactor préalable | `Side` migré de `trading.entities.enums` vers `core.entities.enums` | VO neutre BUY/SELL ; cohérent avec Money/Asset/Currency dans core. Évite à validation de dépendre de trading. |
| Event `ValidationFailed` séparé | Indépendant d'`OrderRejected` côté trading | Romain a tranché Option (c) : traçabilité MIFID II complète, y compris pré-création d'Order. |
| Pas d'event sur succès | Pas de `ValidationApproved` | Évite event spam ; le résultat sync suffit. |
| `ClientLimit` hors-scope phase 1 | Romain a tranché Option (b) | POC ; à ajouter si besoin avec config + UI admin. |

---

## 6. Décisions ouvertes (à trancher en tutorat ou en phase logique)

### 6.1 Logique métier de chaque règle

Tous les `validate(...)` retournent `Optional.empty()` (stubs). À implémenter :
- `BalanceCheckRule` : formule d'exposition par type d'ordre (MARKET BUY, MARKET SELL, LIMIT BUY, LIMIT SELL).
- `CustomerActiveCheckRule` : switch exhaustif sur `CustomerStatus`.
- `KycCheckRule` : matrice `(kycLevel, clientCategory, instrument type)` → autorisé / refusé. Externaliser cette matrice en config plutôt que la hardcoder ?
- `InstrumentStatusCheckRule` : lookup courant du status via `PricingService.findInstrument(...)`.
- `LimitPriceSanityCheckRule` : seuils ±X% configurables.

### 6.2 ClientLimit (hors-scope phase 1)

Si réintroduit plus tard : limites de position max par `(clientId, assetClass)`. Implique :
- Entity `ClientLimit` persistée.
- Règle additionnelle `PositionLimitCheckRule`.
- Endpoint admin pour configurer les limites.
- Lookup additionnel dans le `ValidationContext`.

### 6.3 Calendrier de marché (`MARKET_CLOSED` code)

Le code `MARKET_CLOSED` est défini mais la donnée source (calendrier) n'existe pas encore. À placer :
- **Côté `pricing`** (notre choix par défaut) : pricing connaît déjà les instruments et leur MIC, donc naturel d'y héberger les calendriers.
- Alternative : module `calendar` dédié si la complexité explose (jours fériés CH/EU/US, half-days, etc.).

### 6.4 Idempotence de la validation

Si validation est appelée 2× pour le même `orderId` (retry trading sur timeout), même résultat attendu. Comme la validation est read-only, l'idempotence est triviale tant que les états externes (`Balance`, `Customer`, `LatestQuote`) n'ont pas changé entre les deux appels. À documenter explicitement comme propriété acquise par construction.

### 6.5 Ordre d'application des règles

Question : faut-il un ordre fixé ou les règles peuvent-elles être appliquées en parallèle ? Comme on n'a pas de short-circuit, l'ordre n'affecte pas le résultat (l'ensemble des reasons est identique). Donc parallélisable. Optimisation prématurée pour la phase 1.

### 6.6 Port `ValidationService`

À concevoir :

```java
public interface ValidationService {
    ValidationResult validate(ValidationRequest request);
}
```

Simple. Service construit le contexte (3 lookups : customer, balance, latestQuote), itère sur les règles, agrège, retourne.

### 6.7 Topics Kafka

`ValidationFailed` doit-il aller sur Kafka ? Argument pour : audit MIFID II externe (envoi vers un système d'archive). Argument contre : l'event peut rester local Spring Modulith si l'audit consume in-process. Décision pendante.

### 6.8 Configuration des règles

Chaque règle pourrait porter de la config (seuils, mappings) :
- `LimitPriceSanityCheckRule(BigDecimal lowerBoundPct, BigDecimal upperBoundPct)`
- `KycCheckRule(KycRequirementMatrix matrix)`

Implique :
- Records non-vides
- Injection via `@Configuration` Spring Boot
- ADR à écrire sur la stratégie de configuration

### 6.9 ADRs à écrire

- ADR-017 : Sealed `ValidationRule` + Chain of Responsibility sans short-circuit
- ADR-018 : Sealed `ValidationResult` (Approved | Rejected) — ADT pour pré-trade checks
- ADR-019 : `ValidationFailed` event séparé d'`OrderRejected` (traçabilité MIFID II)
- ADR-020 : `Side` dans `core` (refactor depuis trading)

### 6.10 Tests à écrire (en priorité)

- **Property-based** sur la composition des règles : pour toute combinaison d'états, le résultat est cohérent.
- **Tests unitaires par règle** : isoler chaque `ValidationRule` avec des `ValidationContext` fabriqués.
- **Tests d'intégration** : trading → validation → mocks ledger/pricing/customer, vérifier le flow end-to-end.

---

## 7. Conventions héritées (rappel transverse)

- **Records** pour entités/DTOs/events ; **sealed interfaces** pour hiérarchies fermées.
- **Pattern matching exhaustif** sur sealed et enums.
- **`Instant` UTC** pour les timestamps.
- **Outbox Spring Modulith** pour `ValidationFailed`.
- **Pas de Lombok dans le domaine** — records purs.
- **Make Illegal States Unrepresentable** systématiquement (ici : Approved sans reasons, Rejected avec ≥ 1 reason).

---

## 8. Hors-scope phase actuelle

- Logique métier de chaque règle (tous stubs)
- `ClientLimit` (Romain a tranché : hors-scope POC)
- Calendrier de marché (`MARKET_CLOSED`)
- Configuration injectée des règles
- Port `ValidationService` (interface définie, pas d'implémentation)
- Tests (unitaires, property-based, intégration)
- ADRs
- Métriques Micrometer
- Topics Kafka pour `ValidationFailed`

---

## 9. État du code à ce point

```
io.libra.validation
├── package-info.java                  (@ApplicationModule, allowedDependencies={"core","ledger","pricing","customer"})
├── entities/
│   ├── ValidationRequest.java         (DTO neutre)
│   ├── ValidationResult.java          (sealed)
│   ├── Approved.java                  (record)
│   ├── Rejected.java                  (record, ≥1 reason invariant)
│   ├── ValidationContext.java         (snapshot enrichi)
│   ├── ValidationFailureReason.java   (code + detail)
│   └── enums/
│       └── ValidationFailureCode.java (6 codes)
├── rules/
│   ├── ValidationRule.java            (sealed Chain of Responsibility)
│   ├── BalanceCheckRule.java          (record stub)
│   ├── CustomerActiveCheckRule.java   (record stub)
│   ├── KycCheckRule.java              (record stub)
│   ├── InstrumentStatusCheckRule.java (record stub)
│   └── LimitPriceSanityCheckRule.java (record stub)
└── events/
    └── ValidationFailed.java          (record, ≥1 reason invariant)
```

Refactor associé : `Side` migré dans `io.libra.core.entities.enums` (depuis `io.libra.trading.entities.enums`).

Aucun service implémenté, aucune logique dans les règles, aucune migration Flyway (read-only — pas de DB own).

---

## 10. Reprise de session — prochaines étapes

1. Le data model + structure des règles est **complet** pour la phase 1.
2. Suites possibles :
    - Concevoir le module `settlement` (dernier en data model).
    - Implémenter la logique métier des 5 règles (sortir du tutorat).
    - Implémenter le port `ValidationService` qui orchestre le contexte + les règles.
3. Ne **pas** implémenter spontanément — c'est un projet tutoré.

---

*Document à jour à l'issue de la phase data model + events du module Validation (post Q51 a/b/c).*
