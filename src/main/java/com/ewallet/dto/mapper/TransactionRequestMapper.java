package com.ewallet.dto.mapper;

import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.model.Transaction;
import com.ewallet.service.TypeService;
import com.ewallet.service.BankAccountService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Mapper used for mapping TransactionRequest fields
 */
@Mapper(componentModel = "spring",
        uses = {BankAccountService.class, TypeService.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public abstract class TransactionRequestMapper {

    private BankAccountService BankAccountService;
    private TypeService typeService;

    @Autowired
    public void setBankAccountService(@Lazy BankAccountService BankAccountService) {
        this.BankAccountService = BankAccountService;
    }

    @Autowired
    public void setTypeService(TypeService typeService) {
        this.typeService = typeService;
    }

    // set default value of the status field as Status.SUCCESS
    @Mapping(target = "status", expression = "java(com.ewallet.model.Status.SUCCESS)")
    @Mapping(target = "referenceNumber", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "toLocalDateTime")
    @Mapping(target = "fromBankAccount", ignore = true)
    @Mapping(target = "toBankAccount", ignore = true)
    @Mapping(target = "type", ignore = true)
    public abstract Transaction toEntity(TransactionRequest dto);

    public abstract TransactionRequest toDto(Transaction entity);

    @AfterMapping
    void setToEntityFields(@MappingTarget Transaction entity, TransactionRequest dto) {
        entity.setFromBankAccount(BankAccountService.getByiban(dto.getFromBankAccountiban()));
        entity.setToBankAccount(BankAccountService.getByiban(dto.getToBankAccountiban()));
        entity.setType(typeService.getReferenceById(dto.getTypeId()));
    }

    @Named("toLocalDateTime")
    protected LocalDateTime toLocalDateTime(Instant instant) {
        if (instant != null) {
            return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        } else {
            return null; // or handle it in another appropriate way
        }
    }


    protected Instant map(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
