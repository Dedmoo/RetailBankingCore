package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.Account;
import com.mehmetserin.banking.account.AccountKind;
import com.mehmetserin.banking.account.AccountRepository;
import com.mehmetserin.banking.audit.AuditService;
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

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public TransferService(TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           AccountRepository accountRepository,
                           AppUserRepository userRepository,
                           AuditService auditService,
                           PlatformTransactionManager transactionManager) {
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
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

    public TransferResponse reverse(String username, UUID transferId, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        UUID userId = findUserId(username);

        Optional<Transfer> alreadyProcessed = transferRepository.findByIdempotencyKeyAndInitiatedBy(idempotencyKey, userId);
        if (alreadyProcessed.isPresent()) {
            return TransferResponse.from(alreadyProcessed.get());
        }

        try {
            Transfer reversal = transactionTemplate.execute(status ->
                    executeReversal(userId, transferId, idempotencyKey));
            return TransferResponse.from(reversal);
        } catch (DataIntegrityViolationException race) {
            return transactionTemplate.execute(status -> transferRepository
                    .findByIdempotencyKeyAndInitiatedBy(idempotencyKey, userId)
                    .map(TransferResponse::from)
                    .orElseThrow(() -> race));
        }
    }

    private Transfer executeTransfer(UUID userId, String idempotencyKey, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        Account fromAccount;
        Account toAccount;
        boolean fromIsFirst = request.fromAccountId().compareTo(request.toAccountId()) < 0;
        Account first = lockCustomerAccount(fromIsFirst ? request.fromAccountId() : request.toAccountId());
        Account second = lockCustomerAccount(fromIsFirst ? request.toAccountId() : request.fromAccountId());
        fromAccount = fromIsFirst ? first : second;
        toAccount = fromIsFirst ? second : first;

        if (!fromAccount.getOwnerId().equals(userId)) {
            throw new AccountAccessDeniedException();
        }

        if (!request.currency().equals(fromAccount.getCurrency())
                || !fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new InvalidTransferException("Currency mismatch between accounts and transfer request");
        }

        fromAccount.debit(request.amount());
        toAccount.credit(request.amount());

        Transfer transfer = new Transfer(idempotencyKey, userId, fromAccount.getId(), toAccount.getId(),
                request.amount(), request.currency());
        transferRepository.saveAndFlush(transfer);

        UUID journalId = transfer.getId();
        ledgerEntryRepository.save(LedgerEntry.forTransfer(
                journalId, transfer.getId(), fromAccount.getId(), LedgerEntryType.DEBIT, request.amount()));
        ledgerEntryRepository.save(LedgerEntry.forTransfer(
                journalId, transfer.getId(), toAccount.getId(), LedgerEntryType.CREDIT, request.amount()));

        auditService.record("TRANSFER_POSTED", userId, "TRANSFER", transfer.getId(),
                "amount=" + request.amount() + ";currency=" + request.currency());
        return transfer;
    }

    private Transfer executeReversal(UUID userId, UUID originalTransferId, String idempotencyKey) {
        Transfer original = transferRepository.findById(originalTransferId)
                .orElseThrow(() -> new TransferNotFoundException(originalTransferId));
        if (!original.getInitiatedBy().equals(userId)) {
            throw new AccountAccessDeniedException();
        }
        if (original.getTransferKind() != TransferKind.TRANSFER) {
            throw new InvalidTransferException("Only a normal transfer can be reversed");
        }
        if (transferRepository.findByReversesTransferId(original.getId()).isPresent()) {
            throw new InvalidTransferException("Transfer already reversed");
        }

        // Money moves back: debit original destination, credit original source.
        UUID fromId = original.getToAccountId();
        UUID toId = original.getFromAccountId();
        boolean fromIsFirst = fromId.compareTo(toId) < 0;
        Account first = lockCustomerAccount(fromIsFirst ? fromId : toId);
        Account second = lockCustomerAccount(fromIsFirst ? toId : fromId);
        Account fromAccount = fromIsFirst ? first : second;
        Account toAccount = fromIsFirst ? second : first;

        fromAccount.debit(original.getAmount());
        toAccount.credit(original.getAmount());

        Transfer reversal = new Transfer(idempotencyKey, userId, fromAccount.getId(), toAccount.getId(),
                original.getAmount(), original.getCurrency(), TransferKind.REVERSAL, original.getId());
        transferRepository.saveAndFlush(reversal);

        UUID journalId = reversal.getId();
        ledgerEntryRepository.save(LedgerEntry.forReversal(
                journalId, reversal.getId(), fromAccount.getId(), LedgerEntryType.DEBIT, original.getAmount()));
        ledgerEntryRepository.save(LedgerEntry.forReversal(
                journalId, reversal.getId(), toAccount.getId(), LedgerEntryType.CREDIT, original.getAmount()));

        auditService.record("TRANSFER_REVERSED", userId, "TRANSFER", reversal.getId(),
                "reverses=" + original.getId());
        return reversal;
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

    private Account lockCustomerAccount(UUID accountId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getAccountKind() != AccountKind.CUSTOMER) {
            throw new InvalidTransferException("Transfers are only allowed between customer accounts");
        }
        return account;
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
