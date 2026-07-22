package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.Account;
import com.mehmetserin.banking.account.AccountRepository;
import com.mehmetserin.banking.common.exception.AccountAccessDeniedException;
import com.mehmetserin.banking.common.exception.AccountNotFoundException;
import com.mehmetserin.banking.common.exception.InvalidTransferException;
import com.mehmetserin.banking.common.exception.TransferNotFoundException;
import com.mehmetserin.banking.transfer.dto.TransferRequest;
import com.mehmetserin.banking.transfer.dto.TransferResponse;
import com.mehmetserin.banking.user.AppUser;
import com.mehmetserin.banking.user.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates money movement between two accounts as a double-entry
 * transaction: one DEBIT ledger line on the source account and one CREDIT
 * ledger line on the destination account, both tied to a single {@link Transfer}.
 *
 * <p>Concurrency safety: the two accounts involved are locked with
 * {@code SELECT ... FOR UPDATE} (see {@link AccountRepository#findByIdForUpdate})
 * in a fixed order (lower account id first) so that two transfers racing on the
 * same pair of accounts serialize instead of deadlocking, and so a transfer can
 * never observe a stale balance and overdraw the source account.
 *
 * <p>Idempotency: the caller-supplied {@code Idempotency-Key} is stored with a
 * unique constraint on (idempotency_key, initiated_by). A retried request with
 * the same key returns the original result instead of moving money twice. If
 * two requests with the same key race each other, the loser's insert violates
 * the unique constraint; because Postgres aborts the whole transaction on a
 * constraint violation, the loser's transaction is rolled back (via
 * {@link TransactionTemplate}, since a fresh transaction is required to safely
 * read again after an aborted one) and it re-reads the winner's row instead of
 * failing the request.
 */
@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public TransferService(TransferRepository transferRepository,
                            LedgerEntryRepository ledgerEntryRepository,
                            AccountRepository accountRepository,
                            AppUserRepository userRepository,
                            PlatformTransactionManager transactionManager) {
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TransferResponse transfer(String username, String idempotencyKey, TransferRequest request) {
        requireIdempotencyKey(idempotencyKey);
        UUID userId = findUserId(username);

        Optional<Transfer> alreadyProcessed = transferRepository.findByIdempotencyKeyAndInitiatedBy(idempotencyKey, userId);
        if (alreadyProcessed.isPresent()) {
            return TransferResponse.from(alreadyProcessed.get());
        }

        try {
            Transfer transfer = transactionTemplate.execute(status ->
                    executeTransfer(userId, idempotencyKey, request));
            return TransferResponse.from(transfer);
        } catch (DataIntegrityViolationException raceOnIdempotencyKey) {
            return transactionTemplate.execute(status -> transferRepository
                    .findByIdempotencyKeyAndInitiatedBy(idempotencyKey, userId)
                    .map(TransferResponse::from)
                    .orElseThrow(() -> raceOnIdempotencyKey));
        }
    }

    private Transfer executeTransfer(UUID userId, String idempotencyKey, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        boolean fromIsFirst = request.fromAccountId().compareTo(request.toAccountId()) < 0;
        UUID firstId = fromIsFirst ? request.fromAccountId() : request.toAccountId();
        UUID secondId = fromIsFirst ? request.toAccountId() : request.fromAccountId();

        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);

        Account fromAccount = fromIsFirst ? first : second;
        Account toAccount = fromIsFirst ? second : first;

        // Debit only from an account the caller owns. Credit to another user's
        // account is allowed (simple P2P). Checked after FOR UPDATE so the
        // ownership decision cannot race with a concurrent owner change.
        if (!fromAccount.getOwnerId().equals(userId)) {
            throw new AccountAccessDeniedException();
        }

        if (!request.currency().equals(fromAccount.getCurrency()) || !fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new InvalidTransferException("Currency mismatch between accounts and transfer request");
        }

        fromAccount.debit(request.amount());
        toAccount.credit(request.amount());

        Transfer transfer = new Transfer(idempotencyKey, userId, fromAccount.getId(), toAccount.getId(),
                request.amount(), request.currency());
        transferRepository.saveAndFlush(transfer);

        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), fromAccount.getId(), LedgerEntryType.DEBIT, request.amount()));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), toAccount.getId(), LedgerEntryType.CREDIT, request.amount()));

        return transfer;
    }

    public TransferResponse getMyTransfer(String username, UUID transferId) {
        UUID userId = findUserId(username);
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        if (!transfer.getInitiatedBy().equals(userId)) {
            throw new AccountAccessDeniedException();
        }
        return TransferResponse.from(transfer);
    }

    private Account lockAccount(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private UUID findUserId(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Authenticated user not found: " + username));
        return user.getId();
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidTransferException("Idempotency-Key must not be blank");
        }
        if (idempotencyKey.length() > 100) {
            throw new InvalidTransferException("Idempotency-Key must be at most 100 characters");
        }
    }
}
