package com.mehmetserin.banking.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKeyAndInitiatedBy(String idempotencyKey, UUID initiatedBy);

    Optional<Transfer> findByReversesTransferId(UUID reversesTransferId);
}
