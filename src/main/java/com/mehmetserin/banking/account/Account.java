package com.mehmetserin.banking.account;

import com.mehmetserin.banking.common.exception.InsufficientFundsException;
import com.mehmetserin.banking.common.exception.InvalidAccountStateException;
import com.mehmetserin.banking.common.exception.InvalidTransferException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false, length = 20)
    private AccountKind accountKind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public static Account customer(UUID ownerId, String accountNumber, String currency) {
        return new Account(ownerId, accountNumber, currency, BigDecimal.ZERO, AccountKind.CUSTOMER);
    }

    public static Account house(UUID systemOwnerId, String accountNumber, String currency) {
        return new Account(systemOwnerId, accountNumber, currency, BigDecimal.ZERO, AccountKind.HOUSE);
    }

    private Account(UUID ownerId, String accountNumber, String currency, BigDecimal balance, AccountKind kind) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.balance = balance;
        this.status = AccountStatus.ACTIVE;
        this.accountKind = kind;
        this.createdAt = Instant.now();
    }

    /** @deprecated tests only — prefer {@link #customer} then ledger-funded opening */
    @Deprecated
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
        this.accountKind = AccountKind.CUSTOMER;
        this.createdAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (accountKind == AccountKind.CUSTOMER && balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountNumber);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        balance = balance.add(amount);
    }

    public void freeze() {
        if (accountKind != AccountKind.CUSTOMER) {
            throw new InvalidAccountStateException("Only customer accounts can be frozen");
        }
        if (status == AccountStatus.CLOSED) {
            throw new InvalidAccountStateException("Closed account cannot be frozen");
        }
        if (status == AccountStatus.FROZEN) {
            throw new InvalidAccountStateException("Account is already frozen");
        }
        status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new InvalidAccountStateException("Only a frozen account can be unfrozen");
        }
        status = AccountStatus.ACTIVE;
    }

    public void close() {
        if (accountKind != AccountKind.CUSTOMER) {
            throw new InvalidAccountStateException("Only customer accounts can be closed");
        }
        if (status == AccountStatus.CLOSED) {
            throw new InvalidAccountStateException("Account is already closed");
        }
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidAccountStateException("Account balance must be zero before close");
        }
        status = AccountStatus.CLOSED;
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

    public AccountKind getAccountKind() {
        return accountKind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
