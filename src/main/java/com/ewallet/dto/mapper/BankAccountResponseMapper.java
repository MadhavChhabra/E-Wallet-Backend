package com.ewallet.dto.mapper;

import com.ewallet.dto.response.UserResponse;
import com.ewallet.dto.response.BankAccountResponse;
import com.ewallet.model.User;
import com.ewallet.model.BankAccount;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.text.MessageFormat;

/**
 * Mapper used for mapping BankAccountResponse fields
 */
@Mapper(componentModel = "spring")
public interface BankAccountResponseMapper {

    BankAccount toEntity(BankAccountResponse dto);

    BankAccountResponse toDto(BankAccount entity);

    @AfterMapping
    default void setFullName(@MappingTarget UserResponse dto, User entity) {
        dto.setFullName(MessageFormat.format("{0} {1}", entity.getFirstName(), entity.getLastName()));
    }
}
