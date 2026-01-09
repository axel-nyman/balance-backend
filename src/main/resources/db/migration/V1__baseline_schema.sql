-- V1__baseline_schema.sql
-- Baseline migration: Creates all tables for the budgeting application
-- Generated from JPA entity definitions on 2026-01-09

-- ============================================
-- STANDALONE TABLES (No Foreign Key Dependencies)
-- ============================================

-- Bank Accounts
CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    current_balance NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

-- Partial unique index: enforce unique names only for non-deleted bank accounts
CREATE UNIQUE INDEX idx_bank_accounts_name_active
    ON bank_accounts (name)
    WHERE deleted_at IS NULL;

-- Recurring Expenses
CREATE TABLE recurring_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    recurrence_interval VARCHAR(50) NOT NULL,
    is_manual BOOLEAN NOT NULL,
    last_used_date TIMESTAMP,
    last_used_budget_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

-- Budgets
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    month INTEGER NOT NULL,
    year INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    locked_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_budgets_month_year_deleted UNIQUE (month, year, deleted_at)
);

-- ============================================
-- DEPENDENT TABLES (Have Foreign Key References)
-- ============================================

-- Balance History (references bank_accounts, budgets)
CREATE TABLE balance_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_id UUID NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    change_amount NUMERIC(19, 2) NOT NULL,
    change_date DATE NOT NULL,
    comment VARCHAR(500),
    source VARCHAR(50) NOT NULL,
    budget_id UUID,
    created_at TIMESTAMP NOT NULL
);

-- Budget Income (references budgets, bank_accounts)
CREATE TABLE budget_income (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_budget_income_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT fk_budget_income_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id)
);

-- Budget Expenses (references budgets, bank_accounts, recurring_expenses)
CREATE TABLE budget_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    recurring_expense_id UUID,
    deducted_at DATE,
    is_manual BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_budget_expenses_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT fk_budget_expenses_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT fk_budget_expenses_recurring_expense FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expenses(id)
);

-- Budget Savings (references budgets, bank_accounts)
CREATE TABLE budget_savings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_budget_savings_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT fk_budget_savings_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id)
);

-- Todo Lists (references budgets)
CREATE TABLE todo_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_todo_lists_budget FOREIGN KEY (budget_id) REFERENCES budgets(id)
);

-- Todo Items (references todo_lists, bank_accounts)
CREATE TABLE todo_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    todo_list_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    from_account_id UUID,
    to_account_id UUID,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_todo_items_todo_list FOREIGN KEY (todo_list_id) REFERENCES todo_lists(id),
    CONSTRAINT fk_todo_items_from_account FOREIGN KEY (from_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT fk_todo_items_to_account FOREIGN KEY (to_account_id) REFERENCES bank_accounts(id)
);

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================

-- Foreign key indexes (PostgreSQL doesn't auto-index FKs)
CREATE INDEX idx_balance_history_bank_account ON balance_history(bank_account_id);
CREATE INDEX idx_balance_history_budget ON balance_history(budget_id);
CREATE INDEX idx_budget_income_budget ON budget_income(budget_id);
CREATE INDEX idx_budget_income_bank_account ON budget_income(bank_account_id);
CREATE INDEX idx_budget_expenses_budget ON budget_expenses(budget_id);
CREATE INDEX idx_budget_expenses_bank_account ON budget_expenses(bank_account_id);
CREATE INDEX idx_budget_expenses_recurring ON budget_expenses(recurring_expense_id);
CREATE INDEX idx_budget_savings_budget ON budget_savings(budget_id);
CREATE INDEX idx_budget_savings_bank_account ON budget_savings(bank_account_id);
CREATE INDEX idx_todo_items_todo_list ON todo_items(todo_list_id);
CREATE INDEX idx_todo_items_from_account ON todo_items(from_account_id);
CREATE INDEX idx_todo_items_to_account ON todo_items(to_account_id);

-- Soft delete queries optimization
CREATE INDEX idx_bank_accounts_deleted ON bank_accounts(deleted_at);
CREATE INDEX idx_recurring_expenses_deleted ON recurring_expenses(deleted_at);
CREATE INDEX idx_budgets_deleted ON budgets(deleted_at);
