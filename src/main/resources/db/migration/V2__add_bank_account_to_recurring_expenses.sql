-- Add optional bank account link to recurring expenses
ALTER TABLE recurring_expenses
    ADD COLUMN bank_account_id UUID;

ALTER TABLE recurring_expenses
    ADD CONSTRAINT fk_recurring_expenses_bank_account
    FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id);

CREATE INDEX idx_recurring_expenses_bank_account ON recurring_expenses(bank_account_id);
