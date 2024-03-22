package com.ewallet.dto.mapper;

import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.request.BankAccountRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper used for mapping from BankAccountRequest to TransactionRequest fields
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BankAccountTransactionRequestMapper {

    @Mapping(target = "amount", source = "balance")
    @Mapping(target = "description", constant = "Initial balance")
    @Mapping(target = "fromBankAccountiban", source = "iban")
    @Mapping(target = "toBankAccountiban", source = "iban")
    @Mapping(target = "typeId", constant = "1L")
	TransactionRequest toTransactionDto(BankAccountRequest entity);

    BankAccountRequest toBankAccountDto(TransactionRequest dto);
}
