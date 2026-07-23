package com.mehmetserin.banking.transfer;

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
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_kind", nullable = false, length = 20)
    private TransferKind transferKind;

    @Column(name = "reverses_transfer_id")
    private UUID reversesTransferId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    public Transfer(String idempotencyKey, UUID initiatedBy, UUID fromAccountId, UUID toAccountId,
                    BigDecimal amount, String currency) {
        this(idempotencyKey, initiatedBy, fromAccountId, toAccountId, amount, currency,
                TransferKind.TRANSFER, null);
    }

    public Transfer(String idempotencyKey, UUID initiatedBy, UUID fromAccountId, UUID toAccountId,
                    BigDecimal amount, String currency, TransferKind transferKind, UUID reversesTransferId) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.initiatedBy = initiatedBy;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.transferKind = transferKind;
        this.reversesTransferId = reversesTransferId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferKind getTransferKind() {
        return transferKind;
    }

    public UUID getReversesTransferId() {
        return reversesTransferId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
