-- Partial unique index: enforce unique names only for non-deleted bank accounts (allows name reuse after soft deletion)
CREATE UNIQUE INDEX IF NOT EXISTS idx_bank_accounts_name_active ON bank_accounts (name) WHERE deleted_at IS NULL;
