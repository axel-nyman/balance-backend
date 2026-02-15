-- Rollback: ALTER TABLE recurring_expenses ADD COLUMN last_used_date TIMESTAMP;
ALTER TABLE recurring_expenses DROP COLUMN last_used_date;
