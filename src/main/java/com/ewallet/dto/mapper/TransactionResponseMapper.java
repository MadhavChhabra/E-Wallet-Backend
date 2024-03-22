package com.ewallet.dto.mapper;

import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.model.Transaction;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.ewallet.common.Constants.DATE_TIME_FORMAT;

/**
 * Mapper used for mapping TransactionResponse fields
 */
@Mapper(componentModel = "spring")
public interface TransactionResponseMapper {

    Transaction toEntity(TransactionResponse dto);

    @Mapping(target = "createdAt", ignore = true)
    TransactionResponse toDto(Transaction entity);

    @AfterMapping
    default void formatCreatedAt(@MappingTarget TransactionResponse dto, Transaction entity) {
        if (entity.getCreatedAt() != null) {
            LocalDateTime datetime = LocalDateTime.ofInstant(Instant.from(entity.getCreatedAt()), ZoneOffset.UTC);
            dto.setCreatedAt(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT).format(datetime));
        }
    }

}
