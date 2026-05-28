# Libra — Validation Module Hand-off

> Document de hand-off pour le module `validation` de Libra.
>
> **État** : implémenté et testé (port `ValidationService` + les 5 règles + construction du contexte cross-module). Suite verte (unitaires purs + Testcontainers). Reste : heures de marché (`MARKET_CLOSED`), tiers KYC par instrument, bandes configurables, métriques, ADRs, endpoint REST.

---

## 1. Responsabilité

Le **portier pré-trade** : avant qu'un ordre parte au marché, il vérifie client actif / KYC / instrument tradable / fonds suffisants / prix limite sensé. C'est le **module de convergence** — il consomme `customer` (statut, KYC), `ledger` (balance) et `pricing` (quote). Il ne crée rien : il répond `Approved` ou `Rejected`.

`allowedDependencies = {"core", "ledger", "pricing", "customer"}`. (Pas `reference` : le statut de l'instrument arrive dans la `ValidationRequest`, résolu en amont.)

## 2. Architecture

**Chain of Responsibility.** `ValidationRule` (sealed, 5 règles), chacune `validate(ValidationContext) : Optional<ValidationFailureReason>`. Le service collecte tous les `Optional.of(...)` → **collect-all** (pas fail-fast) : on remonte *toutes* les raisons d'un coup. Vide → `Approved` ; sinon `Rejected` + publie `ValidationFailed` (audit MIFID).

**Contexte enrichi up-front.** `ValidationServiceImpl.buildContext` construit un `ValidationContext` (Customer + Balance + Optional<LatestQuote>) **avant** d'itérer → les règles sont des fonctions pures, aucun fetch dedans.

Résolution du `sourceBalance` :
> `spentAsset` = BUY ? `instrument.quoteAsset()` : `instrument.baseAsset()` → `ledgerService.findClientAccount(clientId, spentAsset)` → `getBalance(account.id())`.

`findClientAccount` (ajouté au port ledger) dérive en interne le `AccountType` de la classe d'asset (`Currency → CLIENT_CASH`, `Security → CLIENT_POSITION`) et cible le compte **final settled** (`pending=false`) — la validation ne voit jamais `AccountType`.

## 3. Les 5 règles

| Règle | Code d'échec | Vérifie |
|---|---|---|
| `CustomerActiveCheckRule` | `CUSTOMER_NOT_ACTIVE` | `customer.status() == ACTIVE` |
| `KycCheckRule` | `KYC_INSUFFICIENT` | `kycLevel != NONE` (tiers par instrument = TODO) |
| `InstrumentStatusCheckRule` | `INSTRUMENT_NOT_TRADABLE` | Security/CurrencyPair `ACTIVE` (switch sealed) |
| `BalanceCheckRule` | `INSUFFICIENT_FUNDS` | `available >= exposition` ; BUY = qty×prix (quote, arrondi CEILING), SELL = qty base |
| `LimitPriceSanityCheckRule` | `LIMIT_PRICE_OUT_OF_BOUNDS` | limite dans `[mid/F, mid×F]`, `F = MAX_PRICE_DEVIATION_FACTOR` (10) |

Exposition BUY = math cross-décimales en `BigDecimal` (qty/10^baseDec × prix/10^priceScale × 10^quoteDec, **arrondi UP** pour ne jamais approuver à la limite). Prix = limite si présente, sinon l'`ask`.

## 4. Décisions clés

| Décision | Choix |
|---|---|
| Chaîne de règles | Sealed `ValidationRule`, collect-all → toutes les raisons d'un coup |
| Contexte | Snapshot construit up-front, règles pures |
| `sourceBalance` | Dérivé de `(instrument, side)` ; type de compte encapsulé dans le ledger |
| Statut instrument | Lu depuis la `request` (snapshot résolu en amont), pas de dépendance `reference` |
| Arrondi exposition | `CEILING` — conservateur (jamais d'approbation à la marge) |
| Event | `ValidationFailed` à chaque rejet (audit, indépendant d'`OrderRejected`) |

## 5. État du code

- `domain` : `ValidationRequest`, `ValidationContext`, `ValidationResult` (sealed `Approved` | `Rejected`), `ValidationFailureReason`, enum `ValidationFailureCode`.
- `rules` : `ValidationRule` (sealed) + les 5 impls (records purs).
- `port` : `ValidationService` / `ValidationServiceImpl`.
- `events` : `ValidationFailed`.
- Ajout côté ledger : `LedgerService.findClientAccount(ownerId, Asset)` + helper privé de dérivation `AccountType`.
- Tests : `ValidationRulesTest` (unitaire pur, chaque règle), `ValidationServiceIntegrationTest` (convergence : client onboardé+activé+financé, pair enregistrée, quote ingérée → Approved ; client suspendu → Rejected).

## 6. Reste à faire (TODO)

- **`MARKET_CLOSED`** : le code enum existe mais aucune règle ne le produit (calendriers d'ouverture différés). Une `MarketHoursCheckRule` quand on aura les calendriers.
- **Tiers KYC par instrument** : aujourd'hui `NONE` bloque tout ; affiner (`BASIC` = FX spot, `ENHANCED` = equity, `FULL` = leveraged) + tenir compte de `clientCategory`.
- **Bandes configurables** : `MAX_PRICE_DEVIATION_FACTOR` fixe → par instrument / via config.
- **Messages trade-only / pas de quote** : un BUY market sans quote n'est pas pricé (skip silencieux) ; à relier au `MARKET_CLOSED` futur.
- Métriques (`validation.rejected.count` taggé par code), ADRs, endpoint, comment `trading` invoque la validation (synchrone avant création d'ordre).

---

*À jour à l'issue de l'implémentation du module validation. Prochain module : `trading` (crée l'ordre, invoque la validation, exécute, déclenche le booking ledger).*
