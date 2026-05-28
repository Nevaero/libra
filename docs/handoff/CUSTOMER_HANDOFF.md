# Libra — Customer Module Hand-off

> Document de hand-off pour le module `customer` de Libra.
>
> **État** : implémenté et testé (port `CustomerService` : onboarding + state machine réglementaire + KYC/risk + events). Suite verte (Testcontainers + unitaires). Reste : PII vault, review périodique, PEP screening, métriques, ADRs, endpoint REST.

---

## 1. Responsabilité

Le **bounded context réglementaire** de Libra : identité civile (PII), dimensions MIFID II / LSFin (catégorie, KYC, profil de risque), cycle de vie du client. Publie les events consommés par `validation` (qui en dérive les capacités de trading) et l'audit. Il **ne** connaît ni les comptes (`ledger` : c'est `Account.ownerId` qui référence `Customer.id`), ni les ordres, ni les prix.

`allowedDependencies = {"core"}`. Domaine sous `io.libra.customer.domain` (records), persistence ACL (`@Data @Entity` + MapStruct).

## 2. Logique implémentée (`CustomerService`)

Tout dans un seul port `CustomerService` (un seul aggregate). Chaque mutation persiste + publie son event via l'outbox.

**State machine** (validée par un helper `transition` à `allowedFrom`) :

```
PENDING_KYC → ACTIVE (activate)   | CLOSED (close)
ACTIVE      → SUSPENDED (suspend) | CLOSED
SUSPENDED   → ACTIVE (reactivate) | CLOSED
CLOSED      = terminal
```

- **`onboard(cmd)`** → `PENDING_KYC` / KYC `NONE`, email unique (rejet si dup), publie `CustomerOnboarded` (Event-Carried State Transfer).
- **`activate`** → PENDING_KYC→ACTIVE, **gated** : refuse si `kycLevel == NONE` (on n'active pas un client dont le KYC n'est pas fait).
- **`suspend` / `reactivate`** → vocabulaire *client global* (vs `freeze`/`unfreeze` ciblé d'un `Account` ledger — ne pas uniformiser).
- **`close`** → terminal, set `closedAt` (l'invariant `closedAt ⇔ CLOSED` est dans le record).
- **`updateKycLevel` / `updateRiskProfile`** → events `KycLevelChanged` / `RiskProfileChanged` (no-op si inchangé).
- **`findById` / `findByEmail`**.

Flux type : `onboard` → `updateKycLevel(BASIC+)` → `activate`.

## 3. Décisions actées (rappel)

| Décision | Choix |
|---|---|
| Bounded context séparé du ledger | PII+KYC+MIFID ≠ comptabilité ; isolation pour audit + chiffrement futur |
| Capacités dérivées par `validation` | Pas de permissions sur `Customer` ; `validation` dérive de `(kyc, risk, category)` — single source of truth |
| PII inline (phase 1) | Vault chiffré = phase 2 (cf. §5) |
| `LocalDate` pour `birthDate` | Date civile, pas un timestamp |
| Record immuable | Changement = nouveau record + event |
| Events | `Onboarded` = ECST ; `*Changed` = notification previous/current |

## 4. État du code

- `domain` : `Customer` (record + invariants), enums `CustomerStatus / KycLevel / RiskProfile / ClientCategory`.
- `commands` : `OnboardCustomerCommand`.
- `events` : `CustomerOnboarded`, `CustomerStatusChanged`, `KycLevelChanged`, `RiskProfileChanged`.
- `port` : `CustomerService` / `CustomerServiceImpl`.
- `persistence` + `repository` : `CustomerEntity` / `CustomerMapper` / `CustomerRepository` (`findByEmail`). Table `customers` déjà dans `V1__schema.sql` (email UNIQUE, CHECK `closed_at ⇔ CLOSED`).
- Tests : `CustomerServiceIntegrationTest` (cycle complet, gating KYC, dup email), `CustomerTest` (invariants record).

## 5. Reste à faire (TODO)

- **PII vault chiffré séparé** (phase 2) : extraire email/nom/birthDate dans une table chiffrée, `Customer` ne garderait qu'un `piiVaultRef` → permet le tombstoning RGPD (conflit AMLA 10 ans / right to erasure). ADR-014/016.
- **Review périodique** KYC + suitability (`lastReviewedAt` + scheduler).
- **PEP screening / sanctions list** (Refinitiv World-Check, Dow Jones) à l'onboarding + périodique.
- **Multi-résidence / nationalité** (FATCA/CRS) : `countryOfResidence` deviendrait un `Set`.
- Métriques Micrometer (`customer.onboarded.count`, `customer.status.transitions.count`…), ADRs (013–016), endpoint REST `/customers`, topic `customer.lifecycle` (clé `customerId`).
- **State machine** : aujourd'hui via `allowedFrom` dans le service. Une `canTransitionTo()` sur l'enum si `validation` en a besoin.

---

*À jour à l'issue de l'implémentation du `CustomerService` (onboarding + lifecycle + events). Prochain module : `validation` (consomme customer + pricing + ledger).*
