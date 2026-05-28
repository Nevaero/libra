-- Equities carry a last-traded price + size alongside the top-of-book quote. Nullable :
-- FX is quote-driven (no central tape) and leaves them NULL. Merged on ingest via COALESCE so
-- a quote-only update never erases the previously seen last trade.
ALTER TABLE latest_quotes
    ADD COLUMN last_price_minor_units BIGINT,
    ADD COLUMN last_size              BIGINT;
