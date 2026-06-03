package com.ewallet.service;

import com.ewallet.dto.mapper.TransactionRequestMapper;
import com.ewallet.dto.mapper.TransactionResponseMapper;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.exception.NoSuchElementFoundException;
import com.ewallet.model.BankAccount;
import com.ewallet.model.OperationType;
import com.ewallet.model.Status;
import com.ewallet.model.Transaction;
import com.ewallet.model.Type;
import com.ewallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.ewallet.common.Constants.*;
import com.ewallet.security.CurrentUserService;

@Slf4j(topic = "TransactionService")
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionRequestMapper transactionRequestMapper;
    private final TransactionResponseMapper transactionResponseMapper;
    private final Clock clock;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public TransactionResponse findById(long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_TRANSACTION));
        assertTransactionAccessible(transaction);
        return transactionResponseMapper.toDto(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findByReferenceNumber(UUID referenceNumber) {
        Transaction transaction = transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new NoSuchElementFoundException(NOT_FOUND_TRANSACTION));
        assertTransactionAccessible(transaction);
        return transactionResponseMapper.toDto(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAllByUserId(Long userId, Pageable pageable) {
        currentUserService.assertUserIdMatchesCurrentUser(userId);
        return transactionRepository.findAllByUserId(userId, pageable)
                .map(transactionResponseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAllForCurrentUser(Pageable pageable) {
        return transactionRepository.findAllByUserId(currentUserService.getCurrentUserId(), pageable)
                .map(transactionResponseMapper::toDto);
    }

    @Transactional
    public CommandResponse create(TransactionRequest request,
                                  BankAccount fromAccount,
                                  BankAccount toAccount,
                                  Type type,
                                  OperationType operationType) {
        Transaction transaction = transactionRequestMapper.toEntity(request);
        transaction.setFromBankAccount(fromAccount);
        transaction.setToBankAccount(toAccount);
        transaction.setType(type);
        transaction.setOperationType(operationType);
        transaction.setReferenceNumber(UUID.randomUUID());
        transaction.setStatus(Status.SUCCESS);
        transaction.setCreatedAt(LocalDateTime.now(clock));

        transactionRepository.save(transaction);
        log.info(CREATED_TRANSACTION,
                transaction.getFromBankAccount().getIban(),
                transaction.getToBankAccount().getIban(),
                transaction.getAmount());
        return CommandResponse.builder().id(transaction.getId()).build();
    }

    private void assertTransactionAccessible(Transaction transaction) {
        Long currentUserId = currentUserService.getCurrentUserId();
        Long fromUserId = transaction.getFromBankAccount().getUser().getId();
        Long toUserId = transaction.getToBankAccount().getUser().getId();

        if (!currentUserId.equals(fromUserId) && !currentUserId.equals(toUserId)) {
            throw new AccessDeniedException("You do not have access to this transaction");
        }
    }
}
