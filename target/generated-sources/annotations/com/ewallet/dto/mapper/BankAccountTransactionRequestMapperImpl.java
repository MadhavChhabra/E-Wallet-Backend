package com.ewallet.dto.mapper;

import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.dto.request.TransactionRequest;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2024-03-21T20:56:31+0530",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.8 (Oracle Corporation)"
)
@Component
public class BankAccountTransactionRequestMapperImpl implements BankAccountTransactionRequestMapper {

    @Override
    public TransactionRequest toTransactionDto(BankAccountRequest entity) {
        if ( entity == null ) {
            return null;
        }

        TransactionRequest transactionRequest = new TransactionRequest();

        transactionRequest.setAmount( entity.getBalance() );
        transactionRequest.setFromBankAccountiban( entity.getIban() );
        transactionRequest.setToBankAccountiban( entity.getIban() );
        transactionRequest.setId( entity.getId() );

        transactionRequest.setDescription( "Initial balance" );
        transactionRequest.setTypeId( (long) 1L );

        return transactionRequest;
    }

    @Override
    public BankAccountRequest toBankAccountDto(TransactionRequest dto) {
        if ( dto == null ) {
            return null;
        }

        BankAccountRequest bankAccountRequest = new BankAccountRequest();

        bankAccountRequest.setId( dto.getId() );

        return bankAccountRequest;
    }
}
