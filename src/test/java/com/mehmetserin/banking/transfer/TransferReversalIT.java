package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.account.dto.StatementLine;
import com.mehmetserin.banking.auth.dto.AuthResponse;
import com.mehmetserin.banking.auth.dto.RegisterRequest;
import com.mehmetserin.banking.support.PostgresIntegrationSupport;
import com.mehmetserin.banking.transfer.dto.TransferRequest;
import com.mehmetserin.banking.transfer.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferReversalIT extends PostgresIntegrationSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void reverseRestoresBalancesAndIsIdempotent() {
        String token = registerAndGetToken("gina");
        AccountResponse source = createAccount(token, "USD", "300.00");
        AccountResponse destination = createAccount(token, "USD", "0.00");

        TransferResponse posted = postTransfer(token, "rev-origin",
                new TransferRequest(source.id(), destination.id(), new BigDecimal("120.00"), "USD")).getBody();

        ResponseEntity<TransferResponse> reversed = restTemplate.exchange(
                "/api/transfers/" + posted.id() + "/reverse", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token, "rev-key-1")), TransferResponse.class);
        assertThat(reversed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reversed.getBody().transferKind()).isEqualTo("REVERSAL");
        assertThat(reversed.getBody().reversesTransferId()).isEqualTo(posted.id());

        assertThat(getAccount(token, source.id()).balance()).isEqualByComparingTo("300.00");
        assertThat(getAccount(token, destination.id()).balance()).isEqualByComparingTo("0.00");

        ResponseEntity<TransferResponse> retry = restTemplate.exchange(
                "/api/transfers/" + posted.id() + "/reverse", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token, "rev-key-1")), TransferResponse.class);
        assertThat(retry.getBody().id()).isEqualTo(reversed.getBody().id());

        ResponseEntity<List<StatementLine>> statement = restTemplate.exchange(
                "/api/accounts/" + source.id() + "/statement", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});
        assertThat(statement.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statement.getBody()).isNotEmpty();
        assertThat(statement.getBody().stream().map(StatementLine::postingKind).toList())
                .contains("OPENING", "TRANSFER", "REVERSAL");
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

    private AccountResponse getAccount(String token, java.util.UUID accountId) {
        ResponseEntity<AccountResponse> response = restTemplate.exchange("/api/accounts/" + accountId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), AccountResponse.class);
        return response.getBody();
    }

    private ResponseEntity<TransferResponse> postTransfer(String token, String key, TransferRequest request) {
        return restTemplate.exchange("/api/transfers", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token, key)), TransferResponse.class);
    }

    private HttpHeaders authHeaders(String token) {
        return authHeaders(token, null);
    }

    private HttpHeaders authHeaders(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        if (idempotencyKey != null) {
            headers.add("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }
}
