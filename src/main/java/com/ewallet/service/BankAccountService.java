package com.ewallet.service;

import com.ewallet.dto.mapper.BankAccountRequestMapper;
import com.ewallet.dto.mapper.BankAccountResponseMapper;
import com.ewallet.dto.mapper.BankAccountTransactionRequestMapper;
import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.BankAccountResponse;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.exception.ConcurrentOperationException;
import com.ewallet.exception.ElementAlreadyExistsException;
import com.ewallet.exception.InsufficientFundsException;
import com.ewallet.exception.NoSuchElementFoundException;
import com.ewallet.model.BankAccount;
import com.ewallet.model.OperationType;
import com.ewallet.model.Type;
import com.ewallet.repository.BankAccountRepository;
import com.ewallet.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.ewallet.common.Constants.*;

@Slf4j(topic = "BankAccountService")
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final TransactionService transactionService;
    private final BankAccountRequestMapper bankAccountRequestMapper;
    private final BankAccountResponseMapper bankAccountResponseMapper;
    private final BankAccountTransactionRequestMapper bankAccountTransactionRequestMapper;
    private final TypeService typeService;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;

    @Transactional(readOnly = true)
    public BankAccountResponse findById(long id) {
        BankAccount account = bankAccountRepository.findByIdWithUser(id)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
        assertAccountOwnedByCurrentUser(account);
        return bankAccountResponseMapper.toDto(account);
    }

    @Transactional(readOnly = true)
    public BankAccountResponse findByIban(String iban) {
        BankAccount account = bankAccountRepository.findByIbanWithUser(iban)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
        assertAccountOwnedByCurrentUser(account);
        return bankAccountResponseMapper.toDto(account);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> findByUserId(long userId) {
        currentUserService.assertUserIdMatchesCurrentUser(userId);
        return bankAccountRepository.findByUserId(userId).stream()
                .map(bankAccountResponseMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<BankAccountResponse> findAllForCurrentUser(Pageable pageable) {
        return bankAccountRepository.findByUserId(currentUserService.getCurrentUserId(), pageable)
                .map(bankAccountResponseMapper::toDto);
    }

    @Transactional
    public CommandResponse create(BankAccountRequest request) {
        currentUserService.assertUserIdMatchesCurrentUser(request.getUserId());

        if (bankAccountRepository.existsByIbanIgnoreCase(request.getIban())) {
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_iban);
        }
        if (bankAccountRepository.existsByUserIdAndNameIgnoreCase(request.getUserId(), request.getName())) {
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_NAME);
        }

        BankAccount bankAccount = bankAccountRequestMapper.toEntity(request);
        bankAccountRepository.save(bankAccount);
        log.info(CREATED_BankAccount, bankAccount.getIban(), bankAccount.getName(), bankAccount.getBalance());

        CommandResponse txResponse = transactionService.create(
                bankAccountTransactionRequestMapper.toTransactionDto(request),
                bankAccount, bankAccount, typeService.getReferenceById(1L), OperationType.INITIAL_DEPOSIT);

        auditLog("CREATE_ACCOUNT", bankAccount.getIban(), bankAccount.getIban(),
                request.getBalance(), txResponse.getId());

        return CommandResponse.builder().id(bankAccount.getId()).build();
    }

    @Transactional
    public CommandResponse transferFunds(TransactionRequest request, String idempotencyKey) {
        return idempotencyService.execute(
                idempotencyKey,
                currentUserService.getCurrentUserId(),
                "TRANSFER",
                hashRequest(request),
                () -> {
                    checkMoneyOperationRateLimit();
                    return doTransferFunds(request);
                });
    }

    @Transactional
    public CommandResponse addFunds(TransactionRequest request, String idempotencyKey) {
        return idempotencyService.execute(
                idempotencyKey,
                currentUserService.getCurrentUserId(),
                "DEPOSIT",
                hashRequest(request),
                () -> {
                    checkMoneyOperationRateLimit();
                    return doAddFunds(request);
                });
    }

    @Transactional
    public CommandResponse withdrawFunds(TransactionRequest request, String idempotencyKey) {
        return idempotencyService.execute(
                idempotencyKey,
                currentUserService.getCurrentUserId(),
                "WITHDRAW",
                hashRequest(request),
                () -> {
                    checkMoneyOperationRateLimit();
                    return doWithdrawFunds(request);
                });
    }

    @Transactional
    public CommandResponse update(BankAccountRequest request) {
        currentUserService.assertUserIdMatchesCurrentUser(request.getUserId());

        BankAccount foundAccount = bankAccountRepository.findByIdWithUser(request.getId())
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
        assertAccountOwnedByCurrentUser(foundAccount);

        if (!request.getIban().equalsIgnoreCase(foundAccount.getIban())
                && bankAccountRepository.existsByIbanIgnoreCase(request.getIban())) {
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_iban);
        }
        if (!request.getName().equalsIgnoreCase(foundAccount.getName())
                && bankAccountRepository.existsByUserIdAndNameIgnoreCase(request.getUserId(), request.getName())) {
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_NAME);
        }

        BankAccount bankAccount = bankAccountRequestMapper.toEntity(request);
        bankAccountRepository.save(bankAccount);
        log.info(UPDATED_BankAccount, bankAccount.getIban(), bankAccount.getName(), bankAccount.getBalance());
        return CommandResponse.builder().id(bankAccount.getId()).build();
    }

    @Transactional
    public void deleteById(long id) {
        BankAccount bankAccount = bankAccountRepository.findByIdWithUser(id)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
        assertAccountOwnedByCurrentUser(bankAccount);
        bankAccountRepository.delete(bankAccount);
        log.info(DELETED_BankAccount, bankAccount.getIban(), bankAccount.getName(), bankAccount.getBalance());
    }

    BankAccount getByIban(String iban) {
        return bankAccountRepository.findByIban(iban)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
    }

    private CommandResponse doTransferFunds(TransactionRequest request) {
        validateAmount(request.getAmount());
        validateDifferentAccounts(request.getFromBankAccountIban(), request.getToBankAccountIban());

        BankAccount fromAccount = findByIbanForUpdate(request.getFromBankAccountIban());
        BankAccount toAccount = findByIbanForUpdate(request.getToBankAccountIban());

        assertAccountOwnedByCurrentUser(fromAccount);

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(FUNDS_CANNOT_BELOW_ZERO);
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        saveAccounts(fromAccount, toAccount);
        log.info(UPDATED_BankAccount_BALANCES, fromAccount.getBalance(), toAccount.getBalance());

        Type type = typeService.getReferenceById(request.getTypeId());
        CommandResponse txResponse = transactionService.create(
                request, fromAccount, toAccount, type, OperationType.TRANSFER);

        auditLog("TRANSFER", fromAccount.getIban(), toAccount.getIban(),
                request.getAmount(), txResponse.getId());

        return txResponse;
    }

    private CommandResponse doAddFunds(TransactionRequest request) {
        validateAmount(request.getAmount());

        BankAccount toAccount = findByIbanForUpdate(request.getToBankAccountIban());
        assertAccountOwnedByCurrentUser(toAccount);

        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
        saveAccounts(toAccount);
        log.info(UPDATED_BankAccount_BALANCE, toAccount.getBalance());

        Type type = typeService.getReferenceById(request.getTypeId());
        CommandResponse txResponse = transactionService.create(
                request, toAccount, toAccount, type, OperationType.DEPOSIT);

        auditLog("DEPOSIT", toAccount.getIban(), toAccount.getIban(),
                request.getAmount(), txResponse.getId());

        return txResponse;
    }

    private CommandResponse doWithdrawFunds(TransactionRequest request) {
        validateAmount(request.getAmount());

        BankAccount fromAccount = findByIbanForUpdate(request.getFromBankAccountIban());
        assertAccountOwnedByCurrentUser(fromAccount);

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(FUNDS_CANNOT_BELOW_ZERO);
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        saveAccounts(fromAccount);
        log.info(UPDATED_BankAccount_BALANCE, fromAccount.getBalance());

        Type type = typeService.getReferenceById(request.getTypeId());
        CommandResponse txResponse = transactionService.create(
                request, fromAccount, fromAccount, type, OperationType.WITHDRAW);

        auditLog("WITHDRAW", fromAccount.getIban(), fromAccount.getIban(),
                request.getAmount(), txResponse.getId());

        return txResponse;
    }

    private BankAccount findByIbanForUpdate(String iban) {
        return bankAccountRepository.findByIbanForUpdate(iban)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
    }

    private void assertAccountOwnedByCurrentUser(BankAccount account) {
        if (!account.getUser().getId().equals(currentUserService.getCurrentUserId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }
    }

    private void checkMoneyOperationRateLimit() {
        rateLimitService.checkTransferLimit("user:" + currentUserService.getCurrentUserId());
    }

    private String hashRequest(TransactionRequest request) {
        return IdempotencyService.hashTransactionRequest(
                request.getFromBankAccountIban(),
                request.getToBankAccountIban(),
                request.getAmount().toPlainString(),
                request.getTypeId());
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void validateDifferentAccounts(String fromIban, String toIban) {
        if (fromIban.equalsIgnoreCase(toIban)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
    }

    private void saveAccounts(BankAccount... accounts) {
        try {
            bankAccountRepository.saveAll(List.of(accounts));
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentOperationException("Account was modified concurrently, please retry");
        }
    }

    private void auditLog(String operation, String fromIban, String toIban,
                          BigDecimal amount, Long transactionId) {
        log.info("AUDIT operation={} fromIban={} toIban={} amount={} transactionId={} userId={}",
                operation, fromIban, toIban, amount, transactionId, currentUserService.getCurrentUserId());
    }
}
