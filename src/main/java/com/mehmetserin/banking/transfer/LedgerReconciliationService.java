package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.Account;
import com.mehmetserin.banking.common.exception.InvalidTransferException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-account: balance = Σ CREDIT − Σ DEBIT.
 * Ledger-wide: Σ DEBIT amounts = Σ CREDIT amounts (every journal is balanced).
 */
@Service
public class LedgerReconciliationService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerReconciliationService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public BigDecimal ledgerBalance(UUID accountId) {
        BigDecimal sum = ledgerEntryRepository.sumSignedAmountByAccountId(accountId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public void assertMatches(Account account) {
        BigDecimal fromLedger = ledgerBalance(account.getId());
        if (account.getBalance().compareTo(fromLedger) != 0) {
            throw new InvalidTransferException(
                    "Ledger out of balance for account " + account.getAccountNumber()
                            + ": stored=" + account.getBalance() + " ledger=" + fromLedger);
        }
    }

    public void assertGlobalDebitsEqualCredits() {
        BigDecimal debits = nullToZero(ledgerEntryRepository.sumAllDebits());
        BigDecimal credits = nullToZero(ledgerEntryRepository.sumAllCredits());
        if (debits.compareTo(credits) != 0) {
            throw new InvalidTransferException(
                    "Global ledger imbalance: debits=" + debits + " credits=" + credits);
        }
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
