package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.Account;
import com.mehmetserin.banking.account.AccountRepository;
import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.auth.dto.AuthResponse;
import com.mehmetserin.banking.auth.dto.RegisterRequest;
import com.mehmetserin.banking.support.PostgresIntegrationSupport;
import com.mehmetserin.banking.transfer.dto.TransferRequest;
import com.mehmetserin.banking.transfer.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerReconciliationIT extends PostgresIntegrationSupport {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private LedgerReconciliationService ledgerReconciliationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storedBalanceMatchesLedgerAfterOpeningAndTransfer() {
        String token = registerAndGetToken("erin");

        AccountResponse source = createAccount(token, "USD", "200.00");
        AccountResponse destination = createAccount(token, "USD", "0.00");

        Account sourceEntity = accountRepository.findById(source.id()).orElseThrow();
        Account destinationEntity = accountRepository.findById(destination.id()).orElseThrow();
        ledgerReconciliationService.assertMatches(sourceEntity);
        ledgerReconciliationService.assertMatches(destinationEntity);

        ResponseEntity<TransferResponse> transfer = postTransfer(token, "recon-1",
                new TransferRequest(source.id(), destination.id(), new BigDecimal("75.00"), "USD"));
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        sourceEntity = accountRepository.findById(source.id()).orElseThrow();
        destinationEntity = accountRepository.findById(destination.id()).orElseThrow();
        ledgerReconciliationService.assertMatches(sourceEntity);
        ledgerReconciliationService.assertMatches(destinationEntity);
        ledgerReconciliationService.assertGlobalDebitsEqualCredits();
        assertThat(sourceEntity.getBalance()).isEqualByComparingTo("125.00");
        assertThat(destinationEntity.getBalance()).isEqualByComparingTo("75.00");

        long openingCredits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ? AND posting_kind = 'OPENING' AND entry_type = 'CREDIT'",
                Long.class, source.id());
        assertThat(openingCredits).isEqualTo(1L);
        long openingDebits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE posting_kind = 'OPENING' AND entry_type = 'DEBIT'",
                Long.class);
        assertThat(openingDebits).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void ledgerEntriesRejectUpdateAndDelete() {
        String token = registerAndGetToken("frank");
        AccountResponse account = createAccount(token, "USD", "50.00");

        UUID ledgerId = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE account_id = ? AND posting_kind = 'OPENING'",
                UUID.class, account.id());
        assertThat(ledgerId).isNotNull();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ledger_entries SET amount = 1 WHERE id = ?", ledgerId))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM ledger_entries WHERE id = ?", ledgerId))
                .hasMessageContaining("append-only");
    }

    private String registerAndGetToken(String username) {
        RegisterRequest request = new RegisterRequest(username, username + "@example.com", "correct-horse-battery");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().accessToken();
    }

    private AccountResponse createAccount(String token, String currency, String openingBalance) {
        CreateAccountRequest request = new CreateAccountRequest(currency, new BigDecimal(openingBalance));
        ResponseEntity<AccountResponse> response = restTemplate.exchange("/api/accounts", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)), AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<TransferResponse> postTransfer(String token, String idempotencyKey, TransferRequest request) {
        HttpHeaders headers = authHeaders(token);
        headers.add("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange("/api/transfers", HttpMethod.POST, new HttpEntity<>(request, headers), TransferResponse.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
