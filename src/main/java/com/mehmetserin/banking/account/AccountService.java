package com.mehmetserin.banking.account;

import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.account.dto.StatementLine;
import com.mehmetserin.banking.audit.AuditService;
import com.mehmetserin.banking.common.exception.AccountAccessDeniedException;
import com.mehmetserin.banking.common.exception.AccountNotFoundException;
import com.mehmetserin.banking.transfer.LedgerEntry;
import com.mehmetserin.banking.transfer.LedgerEntryRepository;
import com.mehmetserin.banking.transfer.LedgerEntryType;
import com.mehmetserin.banking.user.AppUser;
import com.mehmetserin.banking.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AccountService {

    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final HouseFundingService houseFundingService;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository,
                          AppUserRepository userRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          HouseFundingService houseFundingService,
                          AuditService auditService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.houseFundingService = houseFundingService;
        this.auditService = auditService;
    }

    @Transactional
    public AccountResponse createAccount(String username, CreateAccountRequest request) {
        AppUser owner = findUser(username);
        Account customer = Account.customer(owner.getId(), generateAccountNumber(), request.currency());
        accountRepository.save(customer);

        if (request.openingBalance().compareTo(BigDecimal.ZERO) > 0) {
            postOpeningJournal(customer, request.openingBalance());
            customer = accountRepository.findById(customer.getId()).orElseThrow();
        }

        auditService.record("ACCOUNT_OPENED", owner.getId(), "ACCOUNT", customer.getId(),
                "currency=" + customer.getCurrency() + ";opening=" + request.openingBalance());
        return AccountResponse.from(customer);
    }

    private void postOpeningJournal(Account customer, BigDecimal amount) {
        Account funding = houseFundingService.requireFundingAccount(customer.getCurrency());

        boolean customerFirst = customer.getId().compareTo(funding.getId()) < 0;
        Account first = accountRepository.findByIdForUpdate(customerFirst ? customer.getId() : funding.getId())
                .orElseThrow();
        Account second = accountRepository.findByIdForUpdate(customerFirst ? funding.getId() : customer.getId())
                .orElseThrow();

        Account lockedCustomer = first.getId().equals(customer.getId()) ? first : second;
        Account lockedFunding = first.getId().equals(funding.getId()) ? first : second;

        lockedFunding.debit(amount);
        lockedCustomer.credit(amount);

        UUID journalId = UUID.randomUUID();
        ledgerEntryRepository.save(LedgerEntry.forOpening(journalId, lockedFunding.getId(), LedgerEntryType.DEBIT, amount));
        ledgerEntryRepository.save(LedgerEntry.forOpening(journalId, lockedCustomer.getId(), LedgerEntryType.CREDIT, amount));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listMyAccounts(String username) {
        AppUser owner = findUser(username);
        return accountRepository.findByOwnerId(owner.getId()).stream()
                .filter(a -> a.getAccountKind() == AccountKind.CUSTOMER)
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getMyAccount(String username, UUID accountId) {
        return AccountResponse.from(requireOwnedCustomerAccount(username, accountId));
    }

    @Transactional(readOnly = true)
    public List<StatementLine> statement(String username, UUID accountId) {
        Account account = requireOwnedCustomerAccount(username, accountId);
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtAsc(account.getId()).stream()
                .map(StatementLine::from)
                .toList();
    }

    @Transactional
    public AccountResponse freeze(String username, UUID accountId) {
        Account account = requireOwnedCustomerAccount(username, accountId);
        account.freeze();
        auditService.record("ACCOUNT_FROZEN", account.getOwnerId(), "ACCOUNT", account.getId(), null);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse unfreeze(String username, UUID accountId) {
        Account account = requireOwnedCustomerAccount(username, accountId);
        account.unfreeze();
        auditService.record("ACCOUNT_UNFROZEN", account.getOwnerId(), "ACCOUNT", account.getId(), null);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse close(String username, UUID accountId) {
        Account account = requireOwnedCustomerAccount(username, accountId);
        account.close();
        auditService.record("ACCOUNT_CLOSED", account.getOwnerId(), "ACCOUNT", account.getId(), null);
        return AccountResponse.from(account);
    }

    private Account requireOwnedCustomerAccount(String username, UUID accountId) {
        AppUser owner = findUser(username);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getAccountKind() != AccountKind.CUSTOMER || !account.getOwnerId().equals(owner.getId())) {
            throw new AccountAccessDeniedException();
        }
        return account;
    }

    private AppUser findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Authenticated user not found: " + username));
    }

    private String generateAccountNumber() {
        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            String candidate = "TR" + String.format("%010d", Math.abs(random.nextLong() % 10_000_000_000L));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique account number");
    }
}
