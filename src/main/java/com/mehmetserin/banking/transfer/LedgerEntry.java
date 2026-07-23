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
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "posting_kind", nullable = false, length = 20)
    private LedgerPostingKind postingKind;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry forTransfer(UUID journalId, UUID transferId, UUID accountId,
                                          LedgerEntryType entryType, BigDecimal amount) {
        return new LedgerEntry(journalId, transferId, accountId, entryType, LedgerPostingKind.TRANSFER, amount);
    }

    public static LedgerEntry forOpening(UUID journalId, UUID accountId, LedgerEntryType entryType, BigDecimal amount) {
        return new LedgerEntry(journalId, null, accountId, entryType, LedgerPostingKind.OPENING, amount);
    }

    public static LedgerEntry forReversal(UUID journalId, UUID transferId, UUID accountId,
                                          LedgerEntryType entryType, BigDecimal amount) {
        return new LedgerEntry(journalId, transferId, accountId, entryType, LedgerPostingKind.REVERSAL, amount);
    }

    private LedgerEntry(UUID journalId, UUID transferId, UUID accountId, LedgerEntryType entryType,
                        LedgerPostingKind postingKind, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.journalId = journalId;
        this.transferId = transferId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.postingKind = postingKind;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJournalId() {
        return journalId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public LedgerPostingKind getPostingKind() {
        return postingKind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
