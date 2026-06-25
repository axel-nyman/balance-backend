package org.example.axelnyman.main.shared.exceptions;

import org.example.axelnyman.main.domain.dtos.BankAccountDtos.ReallocationConflictResponse;

/**
 * Raised when a manual balance decrease leaves an account over-allocated and the
 * account backs two or more savings goals, so the split of the required reduction
 * is ambiguous. Carries a machine-readable {@link ReallocationConflictResponse}
 * (affected goals, current allocations, required reduction) so the frontend can
 * prompt the user to choose how to split it. Mapped to HTTP 409.
 */
public class AllocationReallocationRequiredException extends RuntimeException {

    private final transient ReallocationConflictResponse detail;

    public AllocationReallocationRequiredException(ReallocationConflictResponse detail) {
        super(detail.error());
        this.detail = detail;
    }

    public ReallocationConflictResponse getDetail() {
        return detail;
    }
}
