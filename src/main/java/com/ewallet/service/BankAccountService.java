package com.ewallet.service;

import com.ewallet.dto.mapper.BankAccountRequestMapper;
import com.ewallet.dto.mapper.BankAccountResponseMapper;
import com.ewallet.dto.mapper.BankAccountTransactionRequestMapper;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.BankAccountResponse;
import com.ewallet.exception.ElementAlreadyExistsException;
import com.ewallet.exception.InsufficientFundsException;
import com.ewallet.exception.NoSuchElementFoundException;
import com.ewallet.model.BankAccount;
import com.ewallet.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ewallet.common.Constants.*;

/**
 * Service used for BankAccount related operations
 */
@Slf4j(topic = "BankAccountService")
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository BankAccountRepository;
    private final TransactionService transactionService;
    private final BankAccountRequestMapper BankAccountRequestMapper;
    private final BankAccountResponseMapper BankAccountResponseMapper;
    private final BankAccountTransactionRequestMapper BankAccountTransactionRequestMapper;

    /**
     * Fetches a single BankAccount by the given id
     *
     * @param id
     * @return BankAccountResponse
     */
    @Transactional(readOnly = true)
    public BankAccountResponse findById(long id) {
        return BankAccountRepository.findById(id)
                .map(BankAccountResponseMapper::toDto)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
    }

    /**
     * Fetches a single BankAccount by the given iban
     *
     * @param iban
     * @return BankAccountResponse
     */
    @Transactional(readOnly = true)
    public BankAccountResponse findByiban(String iban) {
        return BankAccountRepository.findByiban(iban)
                .map(BankAccountResponseMapper::toDto)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
    }

    /**
     * Fetches a single BankAccount by the given userId
     *
     * @param userId
     * @return BankAccountResponse
     */
    @Transactional(readOnly = true)
    public List<BankAccountResponse> findByUserId(long userId) {
        return BankAccountRepository.findByUserId(userId).stream()
                .map(BankAccountResponseMapper::toDto)
                .toList();
    }

    /**
     * Fetches a single BankAccount reference (entity) by the given id
     *
     * @param iban
     * @return BankAccount
     */
    public BankAccount getByiban(String iban) {
        return BankAccountRepository.findByiban(iban)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
    }

    /**
     * Fetches all BankAccounts based on the given paging and sorting parameters
     *
     * @param pageable
     * @return List of BankAccountResponse
     */
    @Transactional(readOnly = true)
    public Page<BankAccountResponse> findAll(Pageable pageable) {
        final Page<BankAccountResponse> BankAccounts = BankAccountRepository.findAll(pageable)
                .map(BankAccountResponseMapper::toDto);
        if (BankAccounts.isEmpty())
            throw new NoSuchElementFoundException(NOT_FOUND_RECORD);
        return BankAccounts;
    }

    /**
     * Creates a new BankAccount using the given request parameters
     *
     * @param request
     * @return id of the created BankAccount
     */
    @Transactional
    public CommandResponse create(BankAccountRequest request) {
        if (BankAccountRepository.existsByibanIgnoreCase(request.getIban()))
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_iban);
        if (BankAccountRepository.existsByUserIdAndNameIgnoreCase(request.getUserId(), request.getName()))
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_NAME);

        final BankAccount BankAccount = BankAccountRequestMapper.toEntity(request);
        BankAccountRepository.save(BankAccount);
        log.info(CREATED_BankAccount, new Object[]{BankAccount.getIban(), BankAccount.getName(), BankAccount.getBalance()});

        // add this initial amount to the transactions
        transactionService.create(BankAccountTransactionRequestMapper.toTransactionDto(request));

        return CommandResponse.builder().id(BankAccount.getId()).build();
    }

    /**
     * Transfer funds between BankAccounts
     *
     * @param request
     * @return id of the transaction
     */
    @Transactional
    public CommandResponse transferFunds(TransactionRequest request) {
        final BankAccount toBankAccount = getByiban(request.getToBankAccountiban());
        final BankAccount fromBankAccount = getByiban(request.getFromBankAccountiban());

        // check if the balance of sender BankAccount has equal or higher to/than transfer amount
        if (fromBankAccount.getBalance().compareTo(request.getAmount()) < 0)
            throw new InsufficientFundsException(FUNDS_CANNOT_BELOW_ZERO);

        // update balance of the sender BankAccount
        fromBankAccount.setBalance(fromBankAccount.getBalance().subtract(request.getAmount()));

        // update balance of the receiver BankAccount
        toBankAccount.setBalance(toBankAccount.getBalance().add(request.getAmount()));

        BankAccountRepository.save(toBankAccount);
        log.info(UPDATED_BankAccount_BALANCES, new Object[]{fromBankAccount.getBalance(), toBankAccount.getBalance()});

        final CommandResponse response = transactionService.create(request);
        return CommandResponse.builder().id(response.getId()).build();
    }

    /**
     * Adds funds to the given BankAccount
     *
     * @param request
     * @return id of the transaction
     */
    @Transactional
    public CommandResponse addFunds(TransactionRequest request) {
        final BankAccount toBankAccount = getByiban(request.getToBankAccountiban());

        // update balance of the receiver BankAccount
        toBankAccount.setBalance(toBankAccount.getBalance().add(request.getAmount()));

        BankAccountRepository.save(toBankAccount);
        log.info(UPDATED_BankAccount_BALANCE, new Object[]{toBankAccount.getBalance()});

        final CommandResponse response = transactionService.create(request);
        return CommandResponse.builder()
                .id(response.getId())
                .build();

    }

    /**
     * Withdraw funds from the given BankAccount
     *
     * @param request
     * @return id of the transaction
     */
    @Transactional
    public CommandResponse withdrawFunds(TransactionRequest request) {
        final BankAccount fromBankAccount = getByiban(request.getFromBankAccountiban());

        // check if the balance of sender BankAccount has equal or higher to/than transfer amount
        if (fromBankAccount.getBalance().compareTo(request.getAmount()) < 0)
            throw new InsufficientFundsException(FUNDS_CANNOT_BELOW_ZERO);

        // update balance of the sender BankAccount
        fromBankAccount.setBalance(fromBankAccount.getBalance().subtract(request.getAmount()));

        BankAccountRepository.save(fromBankAccount);
        log.info(UPDATED_BankAccount_BALANCE, new Object[]{fromBankAccount.getBalance()});

        final CommandResponse response = transactionService.create(request);
        return CommandResponse.builder().id(response.getId()).build();
    }

    /**
     * Updates BankAccount using the given request parameters
     *
     * @param request
     * @return id of the updated BankAccount
     */
    public CommandResponse update(BankAccountRequest request) {
        final BankAccount foundBankAccount = BankAccountRepository.findById(request.getId())
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));

        // check if the iban is changed and new iban is already exists
        if (!request.getIban().equalsIgnoreCase(foundBankAccount.getIban()) &&
                BankAccountRepository.existsByibanIgnoreCase(request.getIban()))
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_iban);

        // check if the name is changed and new name is already exists in user's BankAccounts
        if (!request.getName().equalsIgnoreCase(foundBankAccount.getName()) &&
                BankAccountRepository.existsByUserIdAndNameIgnoreCase(request.getUserId(), request.getName()))
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_BankAccount_NAME);

        final BankAccount BankAccount = BankAccountRequestMapper.toEntity(request);
        BankAccountRepository.save(BankAccount);
        log.info(UPDATED_BankAccount, new Object[]{BankAccount.getIban(), BankAccount.getName(), BankAccount.getBalance()});
        return CommandResponse.builder().id(BankAccount.getId()).build();
    }

    /**
     * Deletes BankAccount by the given id
     *
     * @param id
     */
    public void deleteById(long id) {
        final BankAccount BankAccount = BankAccountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_BankAccount));
        BankAccountRepository.delete(BankAccount);
        log.info(DELETED_BankAccount, new Object[]{BankAccount.getIban(), BankAccount.getName(), BankAccount.getBalance()});
    }
}
