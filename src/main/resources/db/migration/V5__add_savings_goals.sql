-- V5__add_savings_goals.sql
-- Savings goals foundation (item 070a): goals, an allocation ledger over
-- existing account balances, and an append-only allocation-change history.
-- Additive only: the currently deployed backend starts unchanged against this schema.
--
-- Rollback (manual): DROP TABLE goal_allocation_changes, goal_allocations, savings_goals;

-- Savings Goals
CREATE TABLE savings_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    target_amount NUMERIC(19, 2),
    end_date DATE,
    status VARCHAR(50) NOT NULL,
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

-- Goal Allocations: earmark of an account's balance for a goal.
-- At most one active allocation per (goal, account); rows are removed when the
-- amount reaches zero or the goal is archived (history is preserved separately).
CREATE TABLE goal_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    savings_goal_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_goal_allocations_goal FOREIGN KEY (savings_goal_id) REFERENCES savings_goals(id),
    CONSTRAINT fk_goal_allocations_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT uk_goal_allocations_goal_account UNIQUE (savings_goal_id, bank_account_id)
);

-- Goal Allocation Changes: append-only history of every allocation change.
CREATE TABLE goal_allocation_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    savings_goal_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    change_amount NUMERIC(19, 2) NOT NULL,
    resulting_amount NUMERIC(19, 2) NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_goal_allocation_changes_goal FOREIGN KEY (savings_goal_id) REFERENCES savings_goals(id),
    CONSTRAINT fk_goal_allocation_changes_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id)
);

-- Indexes
CREATE INDEX idx_goal_allocations_goal ON goal_allocations(savings_goal_id);
CREATE INDEX idx_goal_allocations_bank_account ON goal_allocations(bank_account_id);
CREATE INDEX idx_goal_allocation_changes_goal ON goal_allocation_changes(savings_goal_id);
CREATE INDEX idx_goal_allocation_changes_bank_account ON goal_allocation_changes(bank_account_id);
CREATE INDEX idx_savings_goals_status ON savings_goals(status);
CREATE INDEX idx_savings_goals_deleted ON savings_goals(deleted_at);
