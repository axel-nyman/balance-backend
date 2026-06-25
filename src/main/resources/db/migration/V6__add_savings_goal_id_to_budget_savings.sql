-- V6__add_savings_goal_id_to_budget_savings.sql
-- Budget-savings ↔ goal linking (item 070c): a budget savings line may
-- optionally reference one savings goal. On budget lock that month's saving is
-- earmarked toward the goal; on unlock it is reversed.
-- Additive only: the column is nullable and existing rows default to NULL, so
-- the currently deployed backend starts unchanged against this schema.
--
-- Rollback (manual):
--   ALTER TABLE budget_savings DROP CONSTRAINT fk_budget_savings_savings_goal;
--   DROP INDEX idx_budget_savings_savings_goal_id;
--   ALTER TABLE budget_savings DROP COLUMN savings_goal_id;

ALTER TABLE budget_savings ADD COLUMN savings_goal_id UUID;

ALTER TABLE budget_savings ADD CONSTRAINT fk_budget_savings_savings_goal
    FOREIGN KEY (savings_goal_id) REFERENCES savings_goals(id);

CREATE INDEX idx_budget_savings_savings_goal_id ON budget_savings(savings_goal_id);
