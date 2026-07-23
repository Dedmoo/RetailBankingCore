package com.mehmetserin.banking.account;

import com.mehmetserin.banking.user.AppUser;
import com.mehmetserin.banking.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HouseFundingService {

    public static final String SYSTEM_USERNAME = "__system__";

    private final AppUserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public HouseFundingService(AppUserRepository userRepository,
                               AccountRepository accountRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Account requireFundingAccount(String currency) {
        String accountNumber = "FUND-" + currency;
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseGet(() -> createFundingAccount(currency, accountNumber));
    }

    private Account createFundingAccount(String currency, String accountNumber) {
        AppUser system = userRepository.findByUsername(SYSTEM_USERNAME)
                .orElseGet(this::createSystemUser);
        Account house = Account.house(system.getId(), accountNumber, currency);
        return accountRepository.save(house);
    }

    private AppUser createSystemUser() {
        // Unusable login: random hash, username reserved.
        AppUser system = new AppUser(SYSTEM_USERNAME, "system@localhost",
                passwordEncoder.encode(UUID.randomUUID().toString()));
        return userRepository.save(system);
    }
}
