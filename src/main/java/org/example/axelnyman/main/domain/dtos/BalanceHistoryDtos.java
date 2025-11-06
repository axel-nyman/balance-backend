package org.example.axelnyman.main.domain.dtos;

import org.example.axelnyman.main.domain.model.BalanceHistorySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BalanceHistoryDtos {

    public record BalanceHistoryResponse(
            UUID id,
            BigDecimal balance,
            BigDecimal changeAmount,
            LocalDateTime changeDate,
            String comment,
            BalanceHistorySource source,
            UUID budgetId
    ) {}

    public record PageMetadata(
            int size,
            int number,
            long totalElements,
            int totalPages
    ) {}

    public record BalanceHistoryPageResponse(
            List<BalanceHistoryResponse> content,
            PageMetadata page
    ) {}
}
