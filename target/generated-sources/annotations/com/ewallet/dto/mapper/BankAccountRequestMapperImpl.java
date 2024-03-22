package com.ewallet.dto.mapper;

import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.model.BankAccount;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2024-03-21T20:56:31+0530",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.8 (Oracle Corporation)"
)
@Component
public class BankAccountRequestMapperImpl extends BankAccountRequestMapper {

    @Override
    public BankAccount toEntity(BankAccountRequest dto) {
        if ( dto == null ) {
            return null;
        }

        BankAccount bankAccount = new BankAccount();

        bankAccount.setId( dto.getId() );
        bankAccount.setBalance( dto.getBalance() );

        bankAccount.setName( org.apache.commons.text.WordUtils.capitalizeFully(dto.getName()) );
        bankAccount.setIban( org.apache.commons.lang3.StringUtils.upperCase(dto.getIban()) );

        setToEntityFields( bankAccount, dto );

        return bankAccount;
    }

    @Override
    public BankAccountRequest toDto(BankAccount entity) {
        if ( entity == null ) {
            return null;
        }

        BankAccountRequest bankAccountRequest = new BankAccountRequest();

        bankAccountRequest.setId( entity.getId() );
        bankAccountRequest.setIban( entity.getIban() );
        bankAccountRequest.setName( entity.getName() );
        bankAccountRequest.setBalance( entity.getBalance() );

        return bankAccountRequest;
    }
}
