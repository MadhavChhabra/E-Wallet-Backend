package com.ewallet.dto.mapper;

import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.model.Transaction;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.format.DateTimeFormatter;

/**
 * Mapper used for mapping TransactionResponse fields
 */
@Mapper(componentModel = "spring")
public interface TransactionResponseMapper {

    // createdAt is a LocalDateTime; emit the day-first, dash-separated format the
    // Flutter clients parse (dd-MM-yyyy HH:mm:ss).
    DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    // Both sides map to TransactionPartyResponse, which has no balance, email or roles: a
    // transaction is visible to both parties, so it must not expose either one's private data.
    @Mapping(target = "createdAt", ignore = true)
    TransactionResponse toDto(Transaction entity);

    @AfterMapping
    default void formatCreatedAt(@MappingTarget TransactionResponse dto, Transaction entity) {
        if (entity.getCreatedAt() != null) {
            dto.setCreatedAt(CREATED_AT_FORMAT.format(entity.getCreatedAt()));
        }
    }

}
