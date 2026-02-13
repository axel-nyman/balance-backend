-- Add month/year tracking columns to recurring_expenses
ALTER TABLE recurring_expenses ADD COLUMN last_used_month INTEGER;
ALTER TABLE recurring_expenses ADD COLUMN last_used_year INTEGER;

-- Backfill from linked budgets: where last_used_budget_id is set,
-- copy month/year from that budget
UPDATE recurring_expenses re
SET last_used_month = b.month,
    last_used_year = b.year
FROM budgets b
WHERE re.last_used_budget_id = b.id
  AND re.last_used_budget_id IS NOT NULL;
