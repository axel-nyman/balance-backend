package org.example.axelnyman.main.api.endpoints;

import jakarta.validation.Valid;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final IDomainService domainService;

    public BudgetController(IDomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(@Valid @RequestBody CreateBudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.createBudget(request));
    }

    @GetMapping
    public ResponseEntity<BudgetListResponse> getAllBudgets() {
        return ResponseEntity.ok(domainService.getAllBudgets());
    }

    @PostMapping("/{budgetId}/income")
    public ResponseEntity<BudgetIncomeResponse> addIncomeToBudget(
            @PathVariable UUID budgetId,
            @Valid @RequestBody CreateBudgetIncomeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.addIncomeToBudget(budgetId, request));
    }
}
