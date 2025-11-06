package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BalanceHistoryDtos.*;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.springframework.data.domain.Page;

public final class BalanceHistoryExtensions {

    private BalanceHistoryExtensions() {
        // Prevent instantiation
    }

    public static BalanceHistoryResponse toResponse(BalanceHistory balanceHistory) {
        return new BalanceHistoryResponse(
                balanceHistory.getId(),
                balanceHistory.getBalance(),
                balanceHistory.getChangeAmount(),
                balanceHistory.getChangeDate(),
                balanceHistory.getComment(),
                balanceHistory.getSource(),
                balanceHistory.getBudgetId()
        );
    }

    public static BalanceHistoryPageResponse toPageResponse(Page<BalanceHistory> page) {
        return new BalanceHistoryPageResponse(
                page.getContent().stream()
                        .map(BalanceHistoryExtensions::toResponse)
                        .toList(),
                new PageMetadata(
                        page.getSize(),
                        page.getNumber(),
                        page.getTotalElements(),
                        page.getTotalPages()
                )
        );
    }
}
