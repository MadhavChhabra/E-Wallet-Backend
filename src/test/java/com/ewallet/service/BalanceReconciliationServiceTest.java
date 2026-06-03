package com.ewallet.service;

import com.ewallet.model.BankAccount;
import com.ewallet.model.OperationType;
import com.ewallet.model.Transaction;
import com.ewallet.repository.BankAccountRepository;
import com.ewallet.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceReconciliationServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BalanceReconciliationService balanceReconciliationService;

    @Test
    void computeExpectedBalance_replaysTransferAndDepositCorrectly() {
        BankAccount accountA = account(1L, "IBAN_A");
        BankAccount accountB = account(2L, "IBAN_B");

        Transaction initialDeposit = tx(accountA, accountA, new BigDecimal("100.00"), OperationType.INITIAL_DEPOSIT, 1);
        Transaction transferOut = tx(accountA, accountB, new BigDecimal("30.00"), OperationType.TRANSFER, 2);
        Transaction deposit = tx(accountA, accountA, new BigDecimal("20.00"), OperationType.DEPOSIT, 3);

        when(transactionRepository.findAllByAccountId(1L))
                .thenReturn(List.of(initialDeposit, transferOut, deposit));

        BigDecimal expected = balanceReconciliationService.computeExpectedBalance(1L);

        assertThat(expected).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    private BankAccount account(Long id, String iban) {
        BankAccount account = new BankAccount();
        account.setId(id);
        account.setIban(iban);
        return account;
    }

    private Transaction tx(BankAccount from, BankAccount to, BigDecimal amount,
                           OperationType operationType, int order) {
        Transaction transaction = new Transaction();
        transaction.setFromBankAccount(from);
        transaction.setToBankAccount(to);
        transaction.setAmount(amount);
        transaction.setOperationType(operationType);
        transaction.setCreatedAt(LocalDateTime.of(2024, 1, order, 0, 0));
        return transaction;
    }
}
