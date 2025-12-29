package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceHistoryRepository extends JpaRepository<BalanceHistory, UUID> {

    @Query("SELECT MAX(bh.changeDate) FROM BalanceHistory bh WHERE bh.bankAccountId = :bankAccountId")
    Optional<LocalDate> findMostRecentChangeDateByBankAccountId(UUID bankAccountId);

    List<BalanceHistory> findAllByBudgetIdAndSource(UUID budgetId, BalanceHistorySource source);

    void deleteAllByBudgetId(UUID budgetId);

    Page<BalanceHistory> findAllByBankAccountIdOrderByChangeDateDescCreatedAtDesc(UUID bankAccountId, Pageable pageable);
}
