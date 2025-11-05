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

    @GetMapping("/{id}")
    public ResponseEntity<BudgetDetailResponse> getBudgetDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(domainService.getBudgetDetails(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable UUID id) {
        domainService.deleteBudget(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{budgetId}/income")
    public ResponseEntity<BudgetIncomeResponse> addIncomeToBudget(
            @PathVariable UUID budgetId,
            @Valid @RequestBody CreateBudgetIncomeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.addIncomeToBudget(budgetId, request));
    }

    @PutMapping("/{budgetId}/income/{id}")
    public ResponseEntity<BudgetIncomeResponse> updateBudgetIncome(
            @PathVariable UUID budgetId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBudgetIncomeRequest request) {
        return ResponseEntity.ok(domainService.updateBudgetIncome(budgetId, id, request));
    }

    @DeleteMapping("/{budgetId}/income/{id}")
    public ResponseEntity<Void> deleteBudgetIncome(
            @PathVariable UUID budgetId,
            @PathVariable UUID id) {
        domainService.deleteBudgetIncome(budgetId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{budgetId}/expenses")
    public ResponseEntity<BudgetExpenseResponse> addExpenseToBudget(
            @PathVariable UUID budgetId,
            @Valid @RequestBody CreateBudgetExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.addExpenseToBudget(budgetId, request));
    }

    @PutMapping("/{budgetId}/expenses/{id}")
    public ResponseEntity<BudgetExpenseResponse> updateBudgetExpense(
            @PathVariable UUID budgetId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBudgetExpenseRequest request) {
        return ResponseEntity.ok(domainService.updateBudgetExpense(budgetId, id, request));
    }

    @DeleteMapping("/{budgetId}/expenses/{id}")
    public ResponseEntity<Void> deleteBudgetExpense(
            @PathVariable UUID budgetId,
            @PathVariable UUID id) {
        domainService.deleteBudgetExpense(budgetId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{budgetId}/savings")
    public ResponseEntity<BudgetSavingsResponse> addSavingsToBudget(
            @PathVariable UUID budgetId,
            @Valid @RequestBody CreateBudgetSavingsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.addSavingsToBudget(budgetId, request));
    }

    @PutMapping("/{budgetId}/savings/{id}")
    public ResponseEntity<BudgetSavingsResponse> updateBudgetSavings(
            @PathVariable UUID budgetId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBudgetSavingsRequest request) {
        return ResponseEntity.ok(domainService.updateBudgetSavings(budgetId, id, request));
    }

    @DeleteMapping("/{budgetId}/savings/{id}")
    public ResponseEntity<Void> deleteBudgetSavings(
            @PathVariable UUID budgetId,
            @PathVariable UUID id) {
        domainService.deleteBudgetSavings(budgetId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/lock")
    public ResponseEntity<BudgetResponse> lockBudget(@PathVariable UUID id) {
        return ResponseEntity.ok(domainService.lockBudget(id));
    }
}
