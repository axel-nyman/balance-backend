package org.example.axelnyman.main.api.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/savings-goals")
@Tag(name = "Savings Goals", description = "Savings goals and account allocation endpoints")
public class SavingsGoalController {

    private final IDomainService domainService;

    public SavingsGoalController(IDomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping
    @Operation(summary = "Create savings goal", description = "Create a savings goal, optionally seeding allocations from accounts' unallocated money")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Savings goal created successfully"),
            @ApiResponse(responseCode = "404", description = "Seed allocation references a missing bank account"),
            @ApiResponse(responseCode = "409", description = "Seed allocation exceeds an account's unallocated balance")
    })
    public ResponseEntity<SavingsGoalResponse> createSavingsGoal(@Valid @RequestBody CreateSavingsGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.createSavingsGoal(request));
    }

    @GetMapping
    @Operation(summary = "Get active savings goals", description = "Retrieve all active savings goals with per-goal allocation summary; archived goals excluded")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Savings goals retrieved successfully")
    })
    public ResponseEntity<SavingsGoalListResponse> getAllSavingsGoals() {
        return ResponseEntity.ok(domainService.getAllSavingsGoals());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get savings goal", description = "Retrieve a savings goal with its per-account allocation breakdown")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Savings goal retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Savings goal not found")
    })
    public ResponseEntity<SavingsGoalResponse> getSavingsGoal(@PathVariable UUID id) {
        return ResponseEntity.ok(domainService.getSavingsGoal(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get allocation history", description = "Retrieve the append-only allocation change history (newest first); available for archived goals too")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Savings goal not found")
    })
    public ResponseEntity<GoalAllocationHistoryResponse> getSavingsGoalHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(domainService.getSavingsGoalHistory(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update savings goal", description = "Edit a goal's name, target amount, and end date")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Savings goal updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or goal is archived"),
            @ApiResponse(responseCode = "404", description = "Savings goal not found")
    })
    public ResponseEntity<SavingsGoalResponse> updateSavingsGoal(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSavingsGoalRequest request) {
        return ResponseEntity.ok(domainService.updateSavingsGoal(id, request));
    }

    @PostMapping("/{id}/allocations")
    @Operation(summary = "Allocate to goal", description = "Set the amount of an account's balance earmarked for the goal; zero removes the allocation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Allocation updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or goal is archived"),
            @ApiResponse(responseCode = "404", description = "Savings goal or bank account not found"),
            @ApiResponse(responseCode = "409", description = "Allocation exceeds the account's unallocated balance")
    })
    public ResponseEntity<SavingsGoalResponse> allocateToGoal(
            @PathVariable UUID id,
            @Valid @RequestBody AllocateRequest request) {
        return ResponseEntity.ok(domainService.allocateToGoal(id, request));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive savings goal", description = "Archive a goal, freeing its allocations; with releaseToBalance=true also reduces backing account balances")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Savings goal archived successfully"),
            @ApiResponse(responseCode = "400", description = "Goal is already archived"),
            @ApiResponse(responseCode = "404", description = "Savings goal not found")
    })
    public ResponseEntity<SavingsGoalResponse> archiveSavingsGoal(
            @PathVariable UUID id,
            @RequestBody(required = false) ArchiveRequest request) {
        return ResponseEntity.ok(domainService.archiveSavingsGoal(
                id, request != null ? request : new ArchiveRequest(false)));
    }
}
