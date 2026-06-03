package com.ewallet.dto.mapper;

import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.model.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransactionRequestMapper {

    @Mapping(target = "operationType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "referenceNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "fromBankAccount", ignore = true)
    @Mapping(target = "toBankAccount", ignore = true)
    @Mapping(target = "type", ignore = true)
    Transaction toEntity(TransactionRequest dto);

    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "toInstant")
    TransactionRequest toDto(Transaction entity);

    @Named("toInstant")
    default Instant toInstant(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    default LocalDateTime map(Instant instant) {
        return instant != null ? instant.atZone(ZoneId.systemDefault()).toLocalDateTime() : null;
    }
}
