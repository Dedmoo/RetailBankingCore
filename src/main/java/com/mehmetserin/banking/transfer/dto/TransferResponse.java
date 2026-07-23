package com.mehmetserin.banking.transfer.dto;

import com.mehmetserin.banking.transfer.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String transferKind,
        UUID reversesTransferId,
        Instant createdAt
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getTransferKind().name(),
                transfer.getReversesTransferId(),
                transfer.getCreatedAt());
    }
}
