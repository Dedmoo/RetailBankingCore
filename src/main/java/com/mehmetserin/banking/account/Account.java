package com.mehmetserin.banking.account;

import com.mehmetserin.banking.common.exception.InsufficientFundsException;
import com.mehmetserin.banking.common.exception.InvalidTransferException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-currency account. Balance mutation is only ever done through
 * {@link #debit(BigDecimal)} and {@link #credit(BigDecimal)} so the invariants
 * (no negative balance, no zero/negative amount movement, no movement on a
 * closed account) hold no matter which service calls into this entity.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(UUID ownerId, String accountNumber, String currency, BigDecimal openingBalance) {
        if (openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidTransferException("Opening balance cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.balance = openingBalance;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountNumber);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Account " + accountNumber + " is not active");
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
