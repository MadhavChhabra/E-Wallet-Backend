package com.ewallet.dto.mapper;

import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.model.Transaction;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2024-03-21T20:56:31+0530",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.8 (Oracle Corporation)"
)
@Component
public class TransactionRequestMapperImpl extends TransactionRequestMapper {

    @Override
    public Transaction toEntity(TransactionRequest dto) {
        if ( dto == null ) {
            return null;
        }

        Transaction transaction = new Transaction();

        transaction.setCreatedAt( toLocalDateTime( dto.getCreatedAt() ) );
        transaction.setId( dto.getId() );
        transaction.setAmount( dto.getAmount() );
        transaction.setDescription( dto.getDescription() );

        transaction.setStatus( com.ewallet.model.Status.SUCCESS );
        transaction.setReferenceNumber( java.util.UUID.randomUUID() );

        setToEntityFields( transaction, dto );

        return transaction;
    }

    @Override
    public TransactionRequest toDto(Transaction entity) {
        if ( entity == null ) {
            return null;
        }

        TransactionRequest transactionRequest = new TransactionRequest();

        transactionRequest.setId( entity.getId() );
        transactionRequest.setAmount( entity.getAmount() );
        transactionRequest.setDescription( entity.getDescription() );
        transactionRequest.setCreatedAt( map( entity.getCreatedAt() ) );
        transactionRequest.setReferenceNumber( entity.getReferenceNumber() );
        transactionRequest.setStatus( entity.getStatus() );

        return transactionRequest;
    }
}
