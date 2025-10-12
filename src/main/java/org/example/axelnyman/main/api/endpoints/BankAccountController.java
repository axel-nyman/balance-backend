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
}
