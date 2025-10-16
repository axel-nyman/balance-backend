package org.example.axelnyman.main.api.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recurring-expenses")
@Tag(name = "Recurring Expenses", description = "Recurring expense template management endpoints")
public class RecurringExpenseController {

    private final IDomainService domainService;

    public RecurringExpenseController(IDomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping
    @Operation(summary = "Create recurring expense template", description = "Create a new recurring expense template with name, amount, interval, and manual flag")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Recurring expense template created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate name")
    })
    public ResponseEntity<RecurringExpenseResponse> createRecurringExpense(@Valid @RequestBody CreateRecurringExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.createRecurringExpense(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update recurring expense template", description = "Update an existing recurring expense template with new values")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recurring expense template updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate name"),
            @ApiResponse(responseCode = "404", description = "Recurring expense not found")
    })
    public ResponseEntity<RecurringExpenseResponse> updateRecurringExpense(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecurringExpenseRequest request) {
        return ResponseEntity.ok(domainService.updateRecurringExpense(id, request));
    }

    @GetMapping
    @Operation(summary = "List all recurring expenses", description = "Get all active recurring expenses with due date calculation and sorted by name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recurring expenses retrieved successfully")
    })
    public ResponseEntity<RecurringExpenseListResponse> getAllRecurringExpenses() {
        return ResponseEntity.ok(domainService.getAllRecurringExpenses());
    }
}
