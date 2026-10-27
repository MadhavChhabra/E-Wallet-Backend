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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void transferFunds_locksAccountsInIbanOrder_forwardDirection() {
        BankAccount iban001 = accountWithBalance("IBAN001", new BigDecimal("100.00"), 1L);
        BankAccount iban002 = accountWithBalance("IBAN002", new BigDecimal("20.00"), 2L);

        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(bankAccountRepository.findByIbanForUpdate("IBAN001")).thenReturn(Optional.of(iban001));
        when(bankAccountRepository.findByIbanForUpdate("IBAN002")).thenReturn(Optional.of(iban002));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(CommandResponse.builder().id(1L).build());

        bankAccountService.transferFunds(transferRequest("IBAN001", "IBAN002", "30.00"), null);

        InOrder lockOrder = inOrder(bankAccountRepository);
        lockOrder.verify(bankAccountRepository).findByIbanForUpdate("IBAN001");
        lockOrder.verify(bankAccountRepository).findByIbanForUpdate("IBAN002");

        assertThat(iban001.getBalance()).isEqualByComparingTo("70.00");
        assertThat(iban002.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferFunds_locksAccountsInIbanOrder_reverseDirection() {
        BankAccount iban001 = accountWithBalance("IBAN001", new BigDecimal("20.00"), 2L);
        BankAccount iban002 = accountWithBalance("IBAN002", new BigDecimal("100.00"), 1L);

        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(bankAccountRepository.findByIbanForUpdate("IBAN001")).thenReturn(Optional.of(iban001));
        when(bankAccountRepository.findByIbanForUpdate("IBAN002")).thenReturn(Optional.of(iban002));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(CommandResponse.builder().id(1L).build());

        bankAccountService.transferFunds(transferRequest("IBAN002", "IBAN001", "30.00"), null);

        // IBAN001 is still locked first, even though the money flows IBAN002 -> IBAN001.
        InOrder lockOrder = inOrder(bankAccountRepository);
        lockOrder.verify(bankAccountRepository).findByIbanForUpdate("IBAN001");
        lockOrder.verify(bankAccountRepository).findByIbanForUpdate("IBAN002");

        assertThat(iban002.getBalance()).isEqualByComparingTo("70.00");
        assertThat(iban001.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferFunds_locksAccountsInIbanOrder_ignoringCase() {
        // The client typed the destination in lowercase. Compared raw, "IBAN002" sorts before
        // "iban001" ('I' < 'i'), which would flip the lock order for this pair of rows.
        BankAccount iban001 = accountWithBalance("IBAN001", new BigDecimal("20.00"), 2L);
        BankAccount iban002 = accountWithBalance("IBAN002", new BigDecimal("100.00"), 1L);

        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(bankAccountRepository.findByIbanForUpdate("iban001")).thenReturn(Optional.of(iban001));
        when(bankAccountRepository.findByIbanForUpdate("IBAN002")).thenReturn(Optional.of(iban002));
        when(transactionService.create(any(), any(), any(), any(), any()))
                .thenReturn(CommandResponse.builder().id(1L).build());

        bankAccountService.transferFunds(transferRequest("IBAN002", "iban001", "30.00"), null);

        InOrder lockOrder = inOrder(bankAccountRepository);
        lockOrder.verify(bankAccountRepository).findByIbanForUpdate("iban001");
        lockOrder.verify(bankAccountRepository).findByIbanForUpdate("IBAN002");

        assertThat(iban002.getBalance()).isEqualByComparingTo("70.00");
        assertThat(iban001.getBalance()).isEqualByComparingTo("50.00");
    }

    private TransactionRequest transferRequest(String fromIban, String toIban, String amount) {
        TransactionRequest request = new TransactionRequest();
        request.setFromBankAccountIban(fromIban);
        request.setToBankAccountIban(toIban);
        request.setAmount(new BigDecimal(amount));
        request.setTypeId(1L);
        return request;
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
