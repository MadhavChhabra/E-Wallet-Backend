package com.ewallet.service;

import com.ewallet.dto.mapper.BankAccountRequestMapper;
import com.ewallet.dto.mapper.BankAccountResponseMapper;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.exception.InsufficientFundsException;
import com.ewallet.model.BankAccount;
import com.ewallet.model.User;
import com.ewallet.repository.BankAccountRepository;
import com.ewallet.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private BankAccountRequestMapper bankAccountRequestMapper;
    @Mock
    private BankAccountResponseMapper bankAccountResponseMapper;
    @Mock
    private TypeService typeService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private BankAccountService bankAccountService;

    @BeforeEach
    void setUpIdempotencyPassthrough() {
        when(idempotencyService.execute(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<CommandResponse> action = invocation.getArgument(4);
                    return action.get();
                });
    }

    @Test
    void transferFunds_insufficientBalance_throwsInsufficientFundsException() {
        BankAccount fromAccount = accountWithBalance("IBAN001", new BigDecimal("10.00"), 1L);
        BankAccount toAccount = accountWithBalance("IBAN002", BigDecimal.ZERO, 2L);

        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(bankAccountRepository.findByIbanForUpdate("IBAN001")).thenReturn(Optional.of(fromAccount));
        when(bankAccountRepository.findByIbanForUpdate("IBAN002")).thenReturn(Optional.of(toAccount));

        TransactionRequest request = new TransactionRequest();
        request.setFromBankAccountIban("IBAN001");
        request.setToBankAccountIban("IBAN002");
        request.setAmount(new BigDecimal("50.00"));
        request.setTypeId(1L);

        assertThatThrownBy(() -> bankAccountService.transferFunds(request, null))
                .isInstanceOf(InsufficientFundsException.class);

        verify(bankAccountRepository, never()).saveAll(any());
        verify(transactionService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void transferFunds_sameAccount_throwsIllegalArgumentException() {
        TransactionRequest request = new TransactionRequest();
        request.setFromBankAccountIban("IBAN001");
        request.setToBankAccountIban("IBAN001");
        request.setAmount(new BigDecimal("10.00"));
        request.setTypeId(1L);

        assertThatThrownBy(() -> bankAccountService.transferFunds(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void transferFunds_zeroAmount_throwsIllegalArgumentException() {
        TransactionRequest request = new TransactionRequest();
        request.setFromBankAccountIban("IBAN001");
        request.setToBankAccountIban("IBAN002");
        request.setAmount(BigDecimal.ZERO);
        request.setTypeId(1L);

        assertThatThrownBy(() -> bankAccountService.transferFunds(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    private BankAccount accountWithBalance(String iban, BigDecimal balance, Long userId) {
        User user = new User();
        user.setId(userId);

        BankAccount account = new BankAccount();
        account.setId(1L);
        account.setIban(iban);
        account.setBalance(balance);
        account.setUser(user);
        account.setVersion(0L);
        return account;
    }
}
