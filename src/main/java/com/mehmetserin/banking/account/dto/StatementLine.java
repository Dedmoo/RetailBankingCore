package com.mehmetserin.banking.account.dto;

import com.mehmetserin.banking.transfer.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementLine(
        UUID id,
        UUID journalId,
        UUID transferId,
        String entryType,
        String postingKind,
        BigDecimal amount,
        Instant createdAt
) {
    public static StatementLine from(LedgerEntry entry) {
        return new StatementLine(
                entry.getId(),
                entry.getJournalId(),
                entry.getTransferId(),
                entry.getEntryType().name(),
                entry.getPostingKind().name(),
                entry.getAmount(),
                entry.getCreatedAt());
    }
}
