package com.ewallet.service;

import com.ewallet.model.BankAccount;
import com.ewallet.model.OperationType;
import com.ewallet.model.Transaction;
import com.ewallet.repository.BankAccountRepository;
import com.ewallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceReconciliationService {

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;

    @Value("${app.reconciliation.enabled:true}")
    private boolean reconciliationEnabled;

    @Scheduled(cron = "${app.reconciliation.cron:0 0 2 * * *}")
    @Transactional(readOnly = true)
    public void reconcileAllAccounts() {
        if (!reconciliationEnabled) {
            return;
        }

        log.info("Starting nightly balance reconciliation");
        int mismatches = 0;

        for (BankAccount account : bankAccountRepository.findAll()) {
            BigDecimal expected = computeExpectedBalance(account.getId());
            if (expected.compareTo(account.getBalance()) != 0) {
                mismatches++;
                log.error(
                        "RECONCILIATION_MISMATCH accountId={} iban={} expected={} actual={}",
                        account.getId(), account.getIban(), expected, account.getBalance());
            }
        }

        log.info("Balance reconciliation complete. mismatches={}", mismatches);
    }

    BigDecimal computeExpectedBalance(Long accountId) {
        List<Transaction> transactions = new ArrayList<>(transactionRepository.findAllByAccountId(accountId));
        transactions.sort(Comparator.comparing(Transaction::getCreatedAt));

        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            balance = applyTransactionEffect(balance, accountId, tx);
        }
        return balance;
    }

    private BigDecimal applyTransactionEffect(BigDecimal balance, Long accountId, Transaction tx) {
        Long fromId = tx.getFromBankAccount().getId();
        Long toId = tx.getToBankAccount().getId();
        BigDecimal amount = tx.getAmount();

        if (fromId.equals(toId)) {
            return switch (tx.getOperationType()) {
                case WITHDRAW -> balance.subtract(amount);
                case DEPOSIT, INITIAL_DEPOSIT -> balance.add(amount);
                case TRANSFER -> balance;
            };
        }

        if (toId.equals(accountId)) {
            balance = balance.add(amount);
        }
        if (fromId.equals(accountId)) {
            balance = balance.subtract(amount);
        }
        return balance;
    }
}
