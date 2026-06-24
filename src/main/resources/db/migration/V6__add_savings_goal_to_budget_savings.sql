-- V6__add_savings_goal_to_budget_savings.sql
-- Savings-goals budget linking (item 070c): a budget savings line may optionally
-- reference a savings goal so that locking the budget earmarks that month's
-- saving toward the goal (and unlocking reverses it).
-- Additive only: the column is nullable, existing rows keep NULL, and the
-- currently deployed backend starts unchanged against this schema.
--
-- Rollback (manual): ALTER TABLE budget_savings DROP COLUMN savings_goal_id;

ALTER TABLE budget_savings
    ADD COLUMN savings_goal_id UUID;

ALTER TABLE budget_savings
    ADD CONSTRAINT fk_budget_savings_savings_goal
        FOREIGN KEY (savings_goal_id) REFERENCES savings_goals(id);

CREATE INDEX idx_budget_savings_savings_goal ON budget_savings(savings_goal_id);
