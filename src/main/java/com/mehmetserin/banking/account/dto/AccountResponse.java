package com.mehmetserin.banking.account.dto;

import com.mehmetserin.banking.account.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        String currency,
        BigDecimal balance,
        String status,
        String accountKind,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus().name(),
                account.getAccountKind().name(),
                account.getCreatedAt());
    }
}
