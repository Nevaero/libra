-- ====================================================================================
-- Libra : schéma initial consolidé.
-- ====================================================================================
-- Conventions transversales :
--   * UUID natif Postgres pour tous les identifiants (le code génère des UUIDv7).
--   * TIMESTAMPTZ pour tous les timestamps (Instant côté Java).
--   * BIGINT en minor units pour tous les montants (jamais NUMERIC, jamais DOUBLE).
--   * snake_case pour toutes les colonnes/contraintes.
--   * Enums Java => VARCHAR + CHECK constraint (alignement @Enumerated(EnumType.STRING)).
--   * Asset polymorphe (Currency | Security) aplati en (asset_type, asset_code, asset_mic) :
--       - asset_type = 'CURRENCY' | 'SECURITY'
--       - asset_code = ISO code (currency) | ticker (security)
--       - asset_mic  = NULL (currency) | venue MIC (security)
--     Le tuple (ticker, mic) est la business identity d'une Security : un ticker recyclé
--     après delisting/relisting ou listé sur plusieurs venues n'a plus à être unique seul.
--   * Instrument polymorphe (Security | CurrencyPair) : FK vers instruments(id), avec
--     un discriminateur instrument_type rehydraté côté Java par InstrumentMapper.
-- ====================================================================================


-- ======================================================================
-- RÉFÉRENTIELS (core, pricing, calendars)
-- ======================================================================

CREATE TABLE currencies (
    code     VARCHAR(3)  PRIMARY KEY,
    name     VARCHAR(64) NOT NULL,
    decimals SMALLINT    NOT NULL CHECK (decimals >= 0 AND decimals <= 8)
);

CREATE TABLE providers (
    id   UUID        PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    code VARCHAR(32) NOT NULL UNIQUE
);

-- Table parent pour le polymorphisme Instrument (Security | CurrencyPair).
-- Permet aux entités métier (Order, Trade, LatestQuote) une FK unique sur instruments(id).
CREATE TABLE instruments (
    id              UUID         PRIMARY KEY,
    instrument_type VARCHAR(20)  NOT NULL
        CHECK (instrument_type IN ('SECURITY', 'CURRENCY_PAIR'))
);

CREATE TABLE securities (
    id                  UUID         PRIMARY KEY REFERENCES instruments(id),
    isin                VARCHAR(12)  NOT NULL UNIQUE,
    ticker              VARCHAR(16)  NOT NULL,
    mic                 VARCHAR(4)   NOT NULL,
    quote_currency_code VARCHAR(3)   NOT NULL REFERENCES currencies(code),
    name                VARCHAR(64)  NOT NULL,
    type                VARCHAR(16)  NOT NULL
        CHECK (type IN ('EQUITY', 'ETF', 'BOND', 'FUTURE', 'OPTION')),
    status              VARCHAR(20)  NOT NULL
        CHECK (status IN ('PENDING_LISTING', 'ACTIVE', 'SUSPENDED', 'HALTED', 'DELISTED')),
    listed_at           TIMESTAMPTZ  NOT NULL,
    delisted_at         TIMESTAMPTZ,
    -- Business identity = (ticker, mic). Ticker seul peut être réutilisé après delisting
    -- ou exister sur plusieurs venues.
    UNIQUE (ticker, mic)
);

CREATE INDEX idx_securities_ticker ON securities(ticker);
CREATE INDEX idx_securities_mic    ON securities(mic);
CREATE INDEX idx_securities_status ON securities(status);

CREATE TABLE currency_pairs (
    id                  UUID        PRIMARY KEY REFERENCES instruments(id),
    base_currency_code  VARCHAR(3)  NOT NULL REFERENCES currencies(code),
    quote_currency_code VARCHAR(3)  NOT NULL REFERENCES currencies(code),
    status              VARCHAR(20) NOT NULL
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    price_scale         SMALLINT    NOT NULL CHECK (price_scale >= 0 AND price_scale <= 8),
    UNIQUE (base_currency_code, quote_currency_code)
);

CREATE TABLE holiday_calendars (
    id   VARCHAR(32)  PRIMARY KEY,
    name VARCHAR(128) NOT NULL
);

CREATE TABLE holidays (
    id                  UUID         PRIMARY KEY,
    holiday_calendar_id VARCHAR(32)  NOT NULL REFERENCES holiday_calendars(id),
    date                DATE         NOT NULL,
    name                VARCHAR(128) NOT NULL,
    UNIQUE (holiday_calendar_id, date)
);

CREATE INDEX idx_holidays_calendar_id ON holidays(holiday_calendar_id);
CREATE INDEX idx_holidays_date        ON holidays(date);


-- ======================================================================
-- CUSTOMER
-- ======================================================================

-- Invariant croisé `closedAt ⇔ status == CLOSED` reproduit en CHECK (defense in depth).
CREATE TABLE customers (
    id                       UUID         PRIMARY KEY,
    email                    VARCHAR(255) NOT NULL UNIQUE,
    first_name               VARCHAR(128) NOT NULL,
    last_name                VARCHAR(128) NOT NULL,
    birth_date               DATE         NOT NULL,
    country_of_residence     CHAR(2)      NOT NULL,
    status                   VARCHAR(20)  NOT NULL
        CHECK (status IN ('PENDING_KYC', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    kyc_level                VARCHAR(20)  NOT NULL
        CHECK (kyc_level IN ('NONE', 'BASIC', 'ENHANCED', 'FULL')),
    risk_profile             VARCHAR(20)  NOT NULL
        CHECK (risk_profile IN ('CONSERVATIVE', 'BALANCED', 'AGGRESSIVE')),
    client_category          VARCHAR(30)  NOT NULL
        CHECK (client_category IN ('RETAIL', 'PROFESSIONAL', 'ELIGIBLE_COUNTERPARTY')),
    onboarded_at             TIMESTAMPTZ  NOT NULL,
    closed_at                TIMESTAMPTZ,
    CONSTRAINT customers_closed_at_consistency CHECK (
        (status =  'CLOSED' AND closed_at IS NOT NULL) OR
        (status <> 'CLOSED' AND closed_at IS NULL)
    )
);

CREATE INDEX idx_customers_status     ON customers(status);
CREATE INDEX idx_customers_kyc_level  ON customers(kyc_level);


-- ======================================================================
-- LEDGER
-- ======================================================================

-- `pending` discrimine :
--   - true  : compte d'engagement non encore settled (phase BOOKING — postings T+0).
--   - false : compte final / settled (phases SETTLEMENT ou IMMEDIATE).
--
-- asset_mic est NULL pour les CURRENCY accounts, populé pour les SECURITY accounts.
CREATE TABLE accounts (
    id         UUID         PRIMARY KEY,
    owner_id   UUID         NOT NULL,
    asset_type VARCHAR(10)  NOT NULL
        CHECK (asset_type IN ('CURRENCY', 'SECURITY')),
    asset_code VARCHAR(20)  NOT NULL,
    asset_mic  VARCHAR(4),
    status     VARCHAR(20)  NOT NULL
        CHECK (status IN ('OPEN', 'CLOSED', 'FROZEN', 'PENDING_ACTIVATION')),
    type       VARCHAR(32)  NOT NULL
        CHECK (type IN ('CLIENT_CASH', 'CLIENT_POSITION', 'LIBRA_FEES', 'LIBRA_CAPITAL',
                        'MARKET_COUNTERPARTY', 'FX_COUNTERPARTY', 'NOSTRO', 'SUSPENSE')),
    pending    BOOLEAN      NOT NULL,
    label      VARCHAR(128),
    created_at TIMESTAMPTZ  NOT NULL,
    closed_at  TIMESTAMPTZ,
    CONSTRAINT accounts_closed_at_consistency CHECK (
        (status =  'CLOSED' AND closed_at IS NOT NULL) OR
        (status <> 'CLOSED' AND closed_at IS NULL)
    ),
    CONSTRAINT accounts_asset_mic_consistency CHECK (
        (asset_type = 'CURRENCY' AND asset_mic IS NULL) OR
        (asset_type = 'SECURITY' AND asset_mic IS NOT NULL)
    )
);

CREATE INDEX idx_accounts_owner_id ON accounts(owner_id);
CREATE INDEX idx_accounts_status   ON accounts(status);
CREATE INDEX idx_accounts_asset    ON accounts(asset_type, asset_code, asset_mic);

-- Projection balance par compte. PK = account_id (1 ligne par compte).
-- Chaque Money embedded = 4 colonnes (minor_units + asset_type + asset_code + asset_mic).
-- Invariant (vérifié côté Java dans le compact constructor de Balance) :
--   available_balance = book_balance - pending_debits + pending_credits
CREATE TABLE balances (
    account_id                       UUID         PRIMARY KEY REFERENCES accounts(id),
    asset_type                       VARCHAR(10)  NOT NULL,
    asset_code                       VARCHAR(20)  NOT NULL,
    asset_mic                        VARCHAR(4),
    book_balance_minor_units         BIGINT       NOT NULL,
    book_balance_asset_type          VARCHAR(10)  NOT NULL,
    book_balance_asset_code          VARCHAR(20)  NOT NULL,
    book_balance_asset_mic           VARCHAR(4),
    available_balance_minor_units    BIGINT       NOT NULL,
    available_balance_asset_type     VARCHAR(10)  NOT NULL,
    available_balance_asset_code     VARCHAR(20)  NOT NULL,
    available_balance_asset_mic      VARCHAR(4),
    pending_debits_minor_units       BIGINT       NOT NULL,
    pending_debits_asset_type        VARCHAR(10)  NOT NULL,
    pending_debits_asset_code        VARCHAR(20)  NOT NULL,
    pending_debits_asset_mic         VARCHAR(4),
    pending_credits_minor_units      BIGINT       NOT NULL,
    pending_credits_asset_type       VARCHAR(10)  NOT NULL,
    pending_credits_asset_code       VARCHAR(20)  NOT NULL,
    pending_credits_asset_mic        VARCHAR(4),
    last_posting_id                  UUID,
    last_posting_sequence_number     BIGINT       NOT NULL DEFAULT 0,
    updated_at                       TIMESTAMPTZ  NOT NULL
);

-- Invariant fondamental (validé côté Java au compact constructor de JournalEntry) :
--   pour chaque asset X dans une entry, SUM(postings DEBIT, asset=X) == SUM(postings CREDIT, asset=X)
--
-- `sequence_number` global monotone : ordre canonique d'audit régulateur.
-- `caused_by` : pour une entry SETTLEMENT, FK vers l'entry BOOKING qu'elle libère.
-- `phase` :
--   - BOOKING    : T+0, postings sur comptes pending (engagement)
--   - SETTLEMENT : T+2, transfert pending -> finaux
--   - IMMEDIATE  : pas de cycle, settled directement (fees, ajustements)
-- Séquence Postgres dédiée pour `sequence_number` (ordre canonique d'audit).
-- Utilisée par PostingService via SELECT nextval('journal_entry_sequence_number').
CREATE SEQUENCE journal_entry_sequence_number AS BIGINT START 1 INCREMENT 1;

CREATE TABLE journal_entries (
    id              UUID         PRIMARY KEY,
    sequence_number BIGINT       NOT NULL UNIQUE,
    entry_type      VARCHAR(32)  NOT NULL,
    phase           VARCHAR(16)  NOT NULL
        CHECK (phase IN ('BOOKING', 'SETTLEMENT', 'IMMEDIATE')),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL,
    description     TEXT,
    caused_by       UUID         REFERENCES journal_entries(id),
    status          VARCHAR(16)  NOT NULL
        CHECK (status IN ('POSTED', 'REVERSED'))
);

CREATE INDEX idx_journal_entries_recorded_at ON journal_entries(recorded_at);
CREATE INDEX idx_journal_entries_caused_by   ON journal_entries(caused_by);

CREATE TABLE postings (
    id                          UUID        PRIMARY KEY,
    journal_entry_id            UUID        NOT NULL REFERENCES journal_entries(id),
    account_id                  UUID        NOT NULL REFERENCES accounts(id),
    sequence_in_entry           BIGINT      NOT NULL,
    amount_minor_units          BIGINT      NOT NULL,
    amount_asset_type           VARCHAR(10) NOT NULL,
    amount_asset_code           VARCHAR(20) NOT NULL,
    amount_asset_mic            VARCHAR(4),
    balance_after_minor_units   BIGINT      NOT NULL,
    balance_after_asset_type    VARCHAR(10) NOT NULL,
    balance_after_asset_code    VARCHAR(20) NOT NULL,
    balance_after_asset_mic     VARCHAR(4),
    type                        VARCHAR(8)  NOT NULL
        CHECK (type IN ('DEBIT', 'CREDIT')),
    UNIQUE (journal_entry_id, sequence_in_entry)
);

CREATE INDEX idx_postings_journal_entry_id ON postings(journal_entry_id);
CREATE INDEX idx_postings_account_id       ON postings(account_id);


-- ======================================================================
-- TRADING
-- ======================================================================

-- Orders : intention de transaction soumise par un client. Cycle de vie :
-- SUBMITTED -> ACCEPTED|REJECTED ; ACCEPTED -> EXECUTED|CANCELLED ; EXECUTED -> SETTLED.
--
-- Collapse du sealed `Order = MarketOrder | LimitOrder` en un seul record discriminé par
-- `order_type` (MARKET|LIMIT). `limit_price_minor_units` n'est non-NULL que pour LIMIT.
--
-- `instrument` (sealed Security | CurrencyPair) -> 2 colonnes (type + id).
-- `quantity` (Money) -> 4 colonnes flatten (minor_units + asset_type + asset_code + asset_mic).
-- `(client_id, idempotency_key)` UNIQUE : idempotence DB-level pour rejouer la soumission.
--
-- parent_order_id : FK vers parent_orders, nullable (un Order peut être standalone).
CREATE TABLE parent_orders (
    id                            UUID         PRIMARY KEY,
    idempotency_key               UUID         NOT NULL,
    client_id                     UUID         NOT NULL,
    submitted_at                  TIMESTAMPTZ  NOT NULL,
    side                          VARCHAR(8)   NOT NULL CHECK (side IN ('BUY','SELL')),
    target_quantity_minor_units   BIGINT       NOT NULL CHECK (target_quantity_minor_units > 0),
    target_quantity_asset_type    VARCHAR(10)  NOT NULL CHECK (target_quantity_asset_type IN ('CURRENCY','SECURITY')),
    target_quantity_asset_code    VARCHAR(20)  NOT NULL,
    target_quantity_asset_mic     VARCHAR(4),
    source_asset_type             VARCHAR(10)  NOT NULL CHECK (source_asset_type IN ('CURRENCY','SECURITY')),
    source_asset_code             VARCHAR(20)  NOT NULL,
    source_asset_mic              VARCHAR(4),
    status                        VARCHAR(20)  NOT NULL CHECK (status IN ('SUBMITTED','ACCEPTED','REJECTED','EXECUTED','CANCELLED','SETTLED')),

    CONSTRAINT parent_orders_idempotency_unique UNIQUE (client_id, idempotency_key),
    CONSTRAINT parent_orders_source_asset_mic_consistency CHECK (
        (source_asset_type = 'CURRENCY' AND source_asset_mic IS NULL) OR
        (source_asset_type = 'SECURITY' AND source_asset_mic IS NOT NULL)
    )
);

CREATE INDEX idx_parent_orders_client_id ON parent_orders(client_id);
CREATE INDEX idx_parent_orders_status    ON parent_orders(status);

CREATE TABLE orders (
    id                       UUID         PRIMARY KEY,
    idempotency_key          UUID         NOT NULL,
    client_id                UUID         NOT NULL,
    submitted_at             TIMESTAMPTZ  NOT NULL,
    instrument_type          VARCHAR(20)  NOT NULL CHECK (instrument_type IN ('SECURITY','CURRENCY_PAIR')),
    instrument_id            UUID         NOT NULL REFERENCES instruments(id),
    side                     VARCHAR(8)   NOT NULL CHECK (side IN ('BUY','SELL')),
    quantity_minor_units     BIGINT       NOT NULL CHECK (quantity_minor_units > 0),
    quantity_asset_type      VARCHAR(10)  NOT NULL CHECK (quantity_asset_type IN ('CURRENCY','SECURITY')),
    quantity_asset_code      VARCHAR(20)  NOT NULL,
    quantity_asset_mic       VARCHAR(4),
    status                   VARCHAR(20)  NOT NULL CHECK (status IN ('SUBMITTED','ACCEPTED','REJECTED','EXECUTED','CANCELLED','SETTLED')),
    order_type               VARCHAR(8)   NOT NULL CHECK (order_type IN ('MARKET','LIMIT')),
    limit_price_minor_units  BIGINT       NULL,
    parent_order_id          UUID         NULL REFERENCES parent_orders(id),

    CONSTRAINT orders_limit_price_consistency CHECK (
        (order_type = 'MARKET' AND limit_price_minor_units IS NULL)
        OR
        (order_type = 'LIMIT' AND limit_price_minor_units IS NOT NULL AND limit_price_minor_units > 0)
    ),

    CONSTRAINT orders_idempotency_unique UNIQUE (client_id, idempotency_key)
);

CREATE INDEX idx_orders_client_id        ON orders(client_id);
CREATE INDEX idx_orders_status           ON orders(status);
CREATE INDEX idx_orders_parent_order_id  ON orders(parent_order_id);

-- Exécutions effectives matchées contre une contrepartie. Un Order peut générer N Trades
-- (cas LIMIT partiellement rempli).
CREATE TABLE trades (
    id                            UUID         PRIMARY KEY,
    order_id                      UUID         NOT NULL,
    counterparty_id               UUID         NOT NULL,
    executed_quantity_minor_units BIGINT       NOT NULL CHECK (executed_quantity_minor_units > 0),
    executed_quantity_asset_type  VARCHAR(10)  NOT NULL,
    executed_quantity_asset_code  VARCHAR(20)  NOT NULL,
    executed_quantity_asset_mic   VARCHAR(4),
    executed_price_minor_units    BIGINT       NOT NULL CHECK (executed_price_minor_units > 0),
    executed_at                   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_trades_order_id        ON trades(order_id);
CREATE INDEX idx_trades_counterparty_id ON trades(counterparty_id);
CREATE INDEX idx_trades_executed_at     ON trades(executed_at);


-- ======================================================================
-- SETTLEMENT
-- ======================================================================

-- Aggregate root du module. Créée à la réception d'un TradeExecuted.
-- Cycle de vie : PENDING -> SETTLED | FAILED.
CREATE TABLE settlement_instructions (
    id                UUID         PRIMARY KEY,
    trade_id          UUID         NOT NULL UNIQUE,
    booking_entry_id  UUID         NOT NULL REFERENCES journal_entries(id),
    value_date        DATE         NOT NULL,
    status            VARCHAR(16)  NOT NULL
        CHECK (status IN ('PENDING', 'SETTLED', 'FAILED')),
    created_at        TIMESTAMPTZ  NOT NULL,
    settled_at        TIMESTAMPTZ,
    failure_reason    VARCHAR(256),
    CONSTRAINT settlement_instructions_status_consistency CHECK (
        (status = 'PENDING' AND settled_at IS NULL     AND failure_reason IS NULL)     OR
        (status = 'SETTLED' AND settled_at IS NOT NULL AND failure_reason IS NULL)     OR
        (status = 'FAILED'  AND settled_at IS NULL     AND failure_reason IS NOT NULL)
    )
);

CREATE INDEX idx_settlement_instructions_value_date ON settlement_instructions(value_date);
CREATE INDEX idx_settlement_instructions_status     ON settlement_instructions(status);

-- Audit trail d'un run du scheduler matinal T+2.
CREATE TABLE settlement_batches (
    id                       UUID         PRIMARY KEY,
    value_date               DATE         NOT NULL,
    run_at                   TIMESTAMPTZ  NOT NULL,
    completed_at             TIMESTAMPTZ,
    instructions_processed   BIGINT       NOT NULL,
    instructions_succeeded   BIGINT       NOT NULL,
    instructions_failed      BIGINT       NOT NULL,
    status                   VARCHAR(20)  NOT NULL
        CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL_FAILURE', 'FAILED')),
    CONSTRAINT settlement_batches_completion_consistency CHECK (
        (status =  'RUNNING' AND completed_at IS NULL) OR
        (status <> 'RUNNING' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT settlement_batches_counts_consistency CHECK (
        instructions_processed = instructions_succeeded + instructions_failed
    )
);

CREATE INDEX idx_settlement_batches_value_date ON settlement_batches(value_date);
CREATE INDEX idx_settlement_batches_run_at     ON settlement_batches(run_at);


-- ======================================================================
-- PRICING
-- ======================================================================

-- Cache "dernier quote connu" par (instrument, tenor). Clé métier UNIQUE (instrument_id, tenor).
-- bid/ask en minor units + price_scale séparé (convention Money minor-units du projet).
CREATE TABLE latest_quotes (
    id              UUID        PRIMARY KEY,
    instrument_id   UUID        NOT NULL REFERENCES instruments(id),
    tenor           VARCHAR(8)  NOT NULL
        CHECK (tenor IN ('TOD', 'TOM', 'SPOT', '_1W', '_1M', '_3M', '_6M', '_1Y')),
    bid_minor_units BIGINT      NOT NULL,
    ask_minor_units BIGINT      NOT NULL,
    bid_size        BIGINT      NOT NULL,
    ask_size        BIGINT      NOT NULL,
    price_scale     SMALLINT    NOT NULL,
    quote_time      TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL,
    provider_id     UUID        NOT NULL REFERENCES providers(id),
    sequence        BIGINT      NOT NULL,
    UNIQUE (instrument_id, tenor)
);

CREATE INDEX idx_latest_quotes_instrument_quote_time
    ON latest_quotes (instrument_id, quote_time DESC);


-- ====================================================================================
-- TRANSACTIONAL OUTBOX — Spring Modulith event_publication
-- ====================================================================================
-- Spring Modulith 2.x ne crée plus la table automatiquement, on la matérialise ici.
-- Cible : JpaEventPublication (spring-modulith-events-jpa).
CREATE TABLE event_publication (
    id                      UUID         PRIMARY KEY,
    listener_id             TEXT         NOT NULL,
    event_type              TEXT         NOT NULL,
    serialized_event        TEXT         NOT NULL,
    publication_date        TIMESTAMPTZ  NOT NULL,
    completion_date         TIMESTAMPTZ,
    completion_attempts     INT          NOT NULL DEFAULT 0,
    last_resubmission_date  TIMESTAMPTZ,
    status                  TEXT
);
CREATE INDEX idx_event_publication_completion_date            ON event_publication (completion_date);
CREATE INDEX idx_event_publication_by_event_and_listener_id   ON event_publication (serialized_event, listener_id);

-- Cible : ArchivedJpaEventPublication (archivage des events traités).
CREATE TABLE event_publication_archive (
    id                      UUID         PRIMARY KEY,
    listener_id             TEXT         NOT NULL,
    event_type              TEXT         NOT NULL,
    serialized_event        TEXT         NOT NULL,
    publication_date        TIMESTAMPTZ  NOT NULL,
    completion_date         TIMESTAMPTZ,
    completion_attempts     INT          NOT NULL DEFAULT 0,
    last_resubmission_date  TIMESTAMPTZ,
    status                  TEXT
);
CREATE INDEX idx_event_publication_archive_completion_date    ON event_publication_archive (completion_date);


-- ====================================================================================
-- SEEDS minimaux pour boot propre + démo.
-- ====================================================================================

-- Currencies de base.
INSERT INTO currencies (code, name, decimals) VALUES
    ('CHF', 'Swiss Franc',    2),
    ('USD', 'US Dollar',      2),
    ('EUR', 'Euro',           2),
    ('JPY', 'Japanese Yen',   0),
    ('GBP', 'British Pound',  2);

-- Provider simulé interne pour POC.
INSERT INTO providers (id, name, code) VALUES
    ('0190a000-0001-7000-8000-000000000001', 'Libra Simulator', 'LIBRA_SIM');

-- Calendriers de jours fériés (POC : structure prête, holidays peuplés à la demande).
INSERT INTO holiday_calendars (id, name) VALUES
    ('CH-BANKING', 'Swiss Banking Holidays'),
    ('US-BANKING', 'US Banking Holidays');

-- Paires FX principales (UUIDv7 statiques — ordonnés chronologiquement).
INSERT INTO instruments (id, instrument_type) VALUES
    ('0190a000-0000-7000-8000-000000000001', 'CURRENCY_PAIR'),
    ('0190a000-0000-7000-8000-000000000002', 'CURRENCY_PAIR'),
    ('0190a000-0000-7000-8000-000000000003', 'CURRENCY_PAIR'),
    ('0190a000-0000-7000-8000-000000000004', 'CURRENCY_PAIR'),
    ('0190a000-0000-7000-8000-000000000005', 'CURRENCY_PAIR');

INSERT INTO currency_pairs (id, base_currency_code, quote_currency_code, status, price_scale) VALUES
    ('0190a000-0000-7000-8000-000000000001', 'EUR', 'USD', 'ACTIVE', 5),
    ('0190a000-0000-7000-8000-000000000002', 'USD', 'CHF', 'ACTIVE', 5),
    ('0190a000-0000-7000-8000-000000000003', 'EUR', 'CHF', 'ACTIVE', 5),
    ('0190a000-0000-7000-8000-000000000004', 'USD', 'JPY', 'ACTIVE', 3),
    ('0190a000-0000-7000-8000-000000000005', 'GBP', 'USD', 'ACTIVE', 5);

-- Securities de démo (NESN sur SIX, AAPL sur XNAS).
INSERT INTO instruments (id, instrument_type) VALUES
    ('0190a000-0003-7000-8000-000000000001', 'SECURITY'),
    ('0190a000-0003-7000-8000-000000000002', 'SECURITY');

INSERT INTO securities (id, isin, ticker, mic, quote_currency_code, name, type, status, listed_at) VALUES
    ('0190a000-0003-7000-8000-000000000001', 'CH0038863350', 'NESN', 'XSWX', 'CHF', 'Nestle SA',  'EQUITY', 'ACTIVE', '2000-01-01T00:00:00Z'),
    ('0190a000-0003-7000-8000-000000000002', 'US0378331005', 'AAPL', 'XNAS', 'USD', 'Apple Inc.', 'EQUITY', 'ACTIVE', '2000-01-01T00:00:00Z');
