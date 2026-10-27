package com.ewallet.dto.mapper;

import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.model.BankAccount;
import com.ewallet.service.UserService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Mapper used for mapping BankAccountRequest fields
 */
@Mapper(componentModel = "spring",
        uses = {UserService.class},
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public abstract class BankAccountRequestMapper {

    private UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Mapping(target = "name", expression = "java(org.apache.commons.text.WordUtils.capitalizeFully(dto.getName()))")
    @Mapping(target = "iban", expression = "java(org.apache.commons.lang3.StringUtils.upperCase(dto.getIban()))")
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "balance", ignore = true)
    public abstract BankAccount toEntity(BankAccountRequest dto);

    public abstract BankAccountRequest toDto(BankAccount entity);

    @AfterMapping
    void setToEntityFields(@MappingTarget BankAccount entity, BankAccountRequest dto) {
        entity.setUser(userService.getReferenceById(dto.getUserId()));
    }
}
