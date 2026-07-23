package com.mehmetserin.banking.account;

import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.account.dto.StatementLine;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@AuthenticationPrincipal String username,
                                                           @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(username, request));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listMyAccounts(@AuthenticationPrincipal String username) {
        return ResponseEntity.ok(accountService.listMyAccounts(username));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@AuthenticationPrincipal String username,
                                                        @PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getMyAccount(username, accountId));
    }

    @GetMapping("/{accountId}/statement")
    public ResponseEntity<List<StatementLine>> statement(@AuthenticationPrincipal String username,
                                                           @PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.statement(username, accountId));
    }

    @PostMapping("/{accountId}/freeze")
    public ResponseEntity<AccountResponse> freeze(@AuthenticationPrincipal String username,
                                                    @PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.freeze(username, accountId));
    }

    @PostMapping("/{accountId}/unfreeze")
    public ResponseEntity<AccountResponse> unfreeze(@AuthenticationPrincipal String username,
                                                      @PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.unfreeze(username, accountId));
    }

    @PostMapping("/{accountId}/close")
    public ResponseEntity<AccountResponse> close(@AuthenticationPrincipal String username,
                                                   @PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.close(username, accountId));
    }
}
