# Libra — Pricing Module Hand-off

> Document de hand-off pour le module `pricing` de Libra.
>
> **État** : implémenté et testé (ingestion + projection + read + bootstrap config-driven). Tout passe en vert (suite Testcontainers). Reste : le **transport réel** (mock-feed externe + clients WS/FIX), métriques, ADRs.

---

## 1. Responsabilité

`pricing` est la **source de vérité market data** : il ingère des ticks de prix venant de providers, maintient la projection lecture `LatestQuote` (top-of-book + last trade), et expose le **prix courant en synchrone** aux autres modules. Il ne gère **pas** le référentiel des instruments — ça, c'est le module `reference` (Security Master). `pricing` résout les instruments via `reference` (par identité métier) au bootstrap.

`allowedDependencies = {"core", "util", "reference"}`.

## 2. Architecture — le flux complet

```
provider (FIX/OANDA, externe)
   │  transport (WS / FIX session) — À FAIRE (cf. §6)
   ▼
PriceProviderClient (adapter, 1 par source)        io.libra.pricing.client(.impl)
   │  parse le format brut → PriceTick normalisé (l'adapter EST l'ACL)
   │  - normalise le `sequence` : passthrough MsgSeqNum (FIX) | dérivé du quoteTime (OANDA)
   ▼
QuoteService.ingestTick(PriceTick)                 io.libra.pricing.service
   │  upsert optimiste conditionnel (sequence = version)
   │  publie QuoteAdvanced SSI le quote avance (affected rows > 0)
   ▼
LatestQuote (read-model)  ◄── getLatestQuote() ◄── PricingService (port public)
```

- **`PriceTick`** = input entrant normalisé (PAS publié). **`QuoteAdvanced`** = event de domaine sortant (outbox), émis seulement quand le quote canonique avance.
- **Adapters** : `FixPriceProviderClient`, `OandaPriceProviderClient` étendent `AbstractPriceProviderClient` (commun : subscriptions, `emit`, helpers de parsing). Un client **par source**, pas par format.
- **DTO bruts** : `client.model.fix.{FixMarketDataSnapshot, FixMdEntry}`, `client.model.json.{OandaPrice, OandaPriceBucket}`. Prix en `String` (forme wire) ; l'adapter parse en minor units.

## 3. Décisions clés (arguments pitch)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Concurrence ingest | **Optimiste** : `INSERT … ON CONFLICT … DO UPDATE … WHERE sequence < EXCLUDED.sequence` | Mono-ligne LWW haute fréquence ≠ pessimiste du ledger (argent, multi-ligne). Lock-free, idempotent sur replay. |
| Ordre des ticks | `sequence` côté source (jamais `receivedAt`) | Delta émission→réception variable. `receivedAt` ne sert qu'à la latence. |
| Normalisation `sequence` | Dans l'**adapter** | Open/Closed : `ingestTick` a une règle unique, le service ignore le provider. |
| Publication event | Conditionnelle (advance only) | Stream propre pour validation/trading/UI ; pas de bruit out-of-order. |
| Last trade (equity) | Champs nullable `lastPrice/lastSize`, **COALESCE-merge** | Un tick quote-only préserve le dernier trade. FX = NULL. |
| Identité instrument (config) | **ISIN+MIC** (securities) / **base+quote** (FX), jamais l'UUID | Stable entre environnements, résolu via `reference` au boot. |
| `priceScale` | Intrinsèque à l'instrument (résolu), pas dans le YAML | Single source of truth. |
| Subscriptions | **YAML** (`pricing-subscriptions.yml`) → `PricingProperties` → bootstrap | Config opérationnelle, pas de hardcode ; providers config-driven. |

## 4. Equities (ETF / stock)

Supporté sans changement structurel : `LatestQuote`/`PriceTick` keyés sur `instrumentId` (Security ou CurrencyPair), `Tenor.SPOT` par convention pour une equity, `priceScale` = décimales de la devise de cotation. Le `FixPriceProviderClient` capte le last trade (`MDEntryType '2'`) ; OANDA (FX) laisse NULL.

## 5. État du code (implémenté + testé)

- `domain` : `LatestQuote` (+ last trade), `Provider`, `Tenor`.
- `events` : `PriceTick` (input), `QuoteAdvanced` (output).
- `service` : `QuoteService` / `QuoteServiceImpl` (ingest + upsert + read).
- `client` : `PriceProviderClient`, `AbstractPriceProviderClient`, `Subscription`, `PriceProviderClientRegistry` ; `client.impl.{Fix,Oanda}PriceProviderClient` ; `client.model.{fix,json}`.
- `port` : `PricingService` (façade read `getLatestQuote`).
- `config` : `PricingProperties`, `PricingSubscriptionBootstrap` (composition root).
- `repository` : `LatestQuoteRepository` (upsert natif + `findByInstrumentIdAndTenor`), `ProviderRepository` (`findByCode`).
- Flyway `V2` : colonnes `last_price_minor_units` / `last_size`.
- Tests Testcontainers : `QuoteServiceIntegrationTest`, `PriceProviderClientIntegrationTest`, `PricingSubscriptionBootstrapTest`.

## 6. Reste à faire (TODO)

### 6.1 ⏭️ Mock-feed externe (transport réel) — PRIORITAIRE, différé

Petit serveur **Bun/Node** (`mock-feed/`, léger) qui génère des prix (random walk + mean reversion) et les streame :
- endpoint WS `/oanda` → JSON OANDA v20 ; endpoint `/fix` → FIX 4.4 texte.
Côté Java : une couche **transport** (clients WebSocket) qui se connecte, lit les frames, parse en `FixMarketDataSnapshot`/`OandaPrice` et appelle `client.onSnapshot/onPrice`. Ça branche sur le seam existant — `QuoteService` et le modèle ne bougent pas.

Notes :
- **Commencer par OANDA** (WS-JSON, le plus proche du réel + simple à parser), FIX-over-WS en 2ᵉ temps.
- WS pour les deux = **simplification de transport assumée** (vrai OANDA = HTTP chunked NDJSON, vrai FIX = session TCP logon/heartbeat/seq). C'est justement ce que l'adapter abstrait → swappable en prod.
- **Garder la suite de tests in-process** (feed direct des clients). Ne PAS rendre les tests dépendants d'un process Bun vivant. Un e2e Testcontainers démarrant le Bun = optionnel, plus tard.

### 6.2 Autres

- Métriques Micrometer : `pricing.tick.ingest.rate`, `pricing.tick.latency` (`receivedAt − quoteTime`), `pricing.tick.outoforder.count` (upsert no-op), `pricing.latestquote.staleness`.
- ADRs : ADR-006 prix en long minor-units + price scale ; ADR-007 concurrence optimiste vs pessimiste (contraste ledger).
- Support des messages **trade-only** (last sale sans bid/ask) → demanderait bid/ask nullables. Différé.
- Multi-provider : aujourd'hui un seul provider par instrument (espaces de `sequence` incomparables sinon). Cf. agrégation best-bid/best-ask plus tard.
- Topics Kafka / partitioning pour `QuoteAdvanced` (clé = `instrumentId`), compaction du flux lifecycle.

---

*À jour à l'issue de l'implémentation pricing (QuoteService + adapters + bootstrap config-driven). Prochaine grosse pièce : le mock-feed §6.1.*
