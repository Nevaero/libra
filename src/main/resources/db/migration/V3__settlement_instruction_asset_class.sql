-- Settlement carries the trade's asset class so the batch can publish the right sealed event
-- (FxTradeSettled vs EquityTradeSettled) without re-reading the trade. Table is empty in phase 1,
-- so NOT NULL adds cleanly.
ALTER TABLE settlement_instructions
    ADD COLUMN asset_class VARCHAR(8) NOT NULL CHECK (asset_class IN ('FX', 'EQUITY'));
