package org.example.axelnyman.main.api.endpoints;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/bank-accounts")
@Tag(name = "Bank Accounts", description = "Bank account management endpoints")
public class BankAccountController {

    private final IDomainService domainService;

    public BankAccountController(IDomainService domainService) {
        this.domainService = domainService;
    }

    @PostMapping
    @Operation(summary = "Create bank account", description = "Create a new bank account with name, description, and initial balance")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Bank account created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate bank account name")
    })
    public ResponseEntity<BankAccountResponse> createBankAccount(@Valid @RequestBody CreateBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.createBankAccount(request));
    }

    @GetMapping
    @Operation(summary = "Get all bank accounts", description = "Retrieve all active bank accounts with total balance and account count")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bank accounts retrieved successfully")
    })
    public ResponseEntity<BankAccountListResponse> getAllBankAccounts() {
        return ResponseEntity.ok(domainService.getAllBankAccounts());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update bank account details", description = "Update name and description of a bank account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account updated successfully"),
            @ApiResponse(responseCode = "400", description = "Duplicate name or validation error"),
            @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public ResponseEntity<BankAccountResponse> updateBankAccountDetails(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBankAccountRequest request) {
        return ResponseEntity.ok(domainService.updateBankAccountDetails(id, request));
    }

    @PostMapping("/{id}/balance")
    @Operation(summary = "Update bank account balance", description = "Manually update the balance of a bank account with date and optional comment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance updated successfully"),
            @ApiResponse(responseCode = "403", description = "Date cannot be in the future"),
            @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public ResponseEntity<BalanceUpdateResponse> updateBankAccountBalance(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBalanceRequest request) {
        return ResponseEntity.ok(domainService.updateBankAccountBalance(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete bank account", description = "Soft delete a bank account by setting deletedAt timestamp")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Bank account deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot delete account used in unlocked budget"),
            @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public ResponseEntity<Void> deleteBankAccount(@PathVariable UUID id) {
        domainService.deleteBankAccount(id);
        return ResponseEntity.noContent().build();
    }
}
