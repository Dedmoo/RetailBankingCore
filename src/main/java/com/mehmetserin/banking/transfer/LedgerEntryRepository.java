package com.mehmetserin.banking.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(UUID accountId);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
            FROM ledger_entries
            WHERE account_id = :accountId
            """, nativeQuery = true)
    BigDecimal sumSignedAmountByAccountId(@Param("accountId") UUID accountId);

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0)
            FROM ledger_entries
            """, nativeQuery = true)
    BigDecimal sumAllDebits();

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0)
            FROM ledger_entries
            """, nativeQuery = true)
    BigDecimal sumAllCredits();
}
