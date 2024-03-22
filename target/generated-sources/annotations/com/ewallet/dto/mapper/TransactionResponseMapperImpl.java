package com.ewallet.dto.mapper;

import com.ewallet.dto.response.BankAccountResponse;
import com.ewallet.dto.response.RoleResponse;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.dto.response.TypeResponse;
import com.ewallet.dto.response.UserResponse;
import com.ewallet.model.BankAccount;
import com.ewallet.model.Role;
import com.ewallet.model.Transaction;
import com.ewallet.model.Type;
import com.ewallet.model.User;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2024-03-21T20:56:31+0530",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.8 (Oracle Corporation)"
)
@Component
public class TransactionResponseMapperImpl implements TransactionResponseMapper {

    @Override
    public Transaction toEntity(TransactionResponse dto) {
        if ( dto == null ) {
            return null;
        }

        Transaction transaction = new Transaction();

        transaction.setId( dto.getId() );
        transaction.setAmount( dto.getAmount() );
        transaction.setDescription( dto.getDescription() );
        if ( dto.getCreatedAt() != null ) {
            transaction.setCreatedAt( LocalDateTime.parse( dto.getCreatedAt() ) );
        }
        transaction.setReferenceNumber( dto.getReferenceNumber() );
        transaction.setStatus( dto.getStatus() );
        transaction.setFromBankAccount( bankAccountResponseToBankAccount( dto.getFromBankAccount() ) );
        transaction.setToBankAccount( bankAccountResponseToBankAccount( dto.getToBankAccount() ) );
        transaction.setType( typeResponseToType( dto.getType() ) );

        return transaction;
    }

    @Override
    public TransactionResponse toDto(Transaction entity) {
        if ( entity == null ) {
            return null;
        }

        TransactionResponse transactionResponse = new TransactionResponse();

        transactionResponse.setId( entity.getId() );
        transactionResponse.setAmount( entity.getAmount() );
        transactionResponse.setDescription( entity.getDescription() );
        transactionResponse.setReferenceNumber( entity.getReferenceNumber() );
        transactionResponse.setStatus( entity.getStatus() );
        transactionResponse.setFromBankAccount( bankAccountToBankAccountResponse( entity.getFromBankAccount() ) );
        transactionResponse.setToBankAccount( bankAccountToBankAccountResponse( entity.getToBankAccount() ) );
        transactionResponse.setType( typeToTypeResponse( entity.getType() ) );

        formatCreatedAt( transactionResponse, entity );

        return transactionResponse;
    }

    protected Role roleResponseToRole(RoleResponse roleResponse) {
        if ( roleResponse == null ) {
            return null;
        }

        Role role = new Role();

        role.setId( roleResponse.getId() );
        role.setType( roleResponse.getType() );

        return role;
    }

    protected Set<Role> roleResponseSetToRoleSet(Set<RoleResponse> set) {
        if ( set == null ) {
            return null;
        }

        Set<Role> set1 = new LinkedHashSet<Role>( Math.max( (int) ( set.size() / .75f ) + 1, 16 ) );
        for ( RoleResponse roleResponse : set ) {
            set1.add( roleResponseToRole( roleResponse ) );
        }

        return set1;
    }

    protected User userResponseToUser(UserResponse userResponse) {
        if ( userResponse == null ) {
            return null;
        }

        User user = new User();

        user.setId( userResponse.getId() );
        user.setFirstName( userResponse.getFirstName() );
        user.setLastName( userResponse.getLastName() );
        user.setUsername( userResponse.getUsername() );
        user.setEmail( userResponse.getEmail() );
        user.setRoles( roleResponseSetToRoleSet( userResponse.getRoles() ) );

        return user;
    }

    protected BankAccount bankAccountResponseToBankAccount(BankAccountResponse bankAccountResponse) {
        if ( bankAccountResponse == null ) {
            return null;
        }

        BankAccount bankAccount = new BankAccount();

        bankAccount.setId( bankAccountResponse.getId() );
        bankAccount.setIban( bankAccountResponse.getIban() );
        bankAccount.setName( bankAccountResponse.getName() );
        bankAccount.setBalance( bankAccountResponse.getBalance() );
        bankAccount.setUser( userResponseToUser( bankAccountResponse.getUser() ) );

        return bankAccount;
    }

    protected Type typeResponseToType(TypeResponse typeResponse) {
        if ( typeResponse == null ) {
            return null;
        }

        Type type = new Type();

        type.setId( typeResponse.getId() );
        type.setName( typeResponse.getName() );
        type.setDescription( typeResponse.getDescription() );

        return type;
    }

    protected RoleResponse roleToRoleResponse(Role role) {
        if ( role == null ) {
            return null;
        }

        RoleResponse roleResponse = new RoleResponse();

        roleResponse.setId( role.getId() );
        roleResponse.setType( role.getType() );

        return roleResponse;
    }

    protected Set<RoleResponse> roleSetToRoleResponseSet(Set<Role> set) {
        if ( set == null ) {
            return null;
        }

        Set<RoleResponse> set1 = new LinkedHashSet<RoleResponse>( Math.max( (int) ( set.size() / .75f ) + 1, 16 ) );
        for ( Role role : set ) {
            set1.add( roleToRoleResponse( role ) );
        }

        return set1;
    }

    protected UserResponse userToUserResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse userResponse = new UserResponse();

        userResponse.setId( user.getId() );
        userResponse.setFirstName( user.getFirstName() );
        userResponse.setLastName( user.getLastName() );
        userResponse.setUsername( user.getUsername() );
        userResponse.setEmail( user.getEmail() );
        userResponse.setRoles( roleSetToRoleResponseSet( user.getRoles() ) );

        return userResponse;
    }

    protected BankAccountResponse bankAccountToBankAccountResponse(BankAccount bankAccount) {
        if ( bankAccount == null ) {
            return null;
        }

        BankAccountResponse bankAccountResponse = new BankAccountResponse();

        bankAccountResponse.setId( bankAccount.getId() );
        bankAccountResponse.setIban( bankAccount.getIban() );
        bankAccountResponse.setName( bankAccount.getName() );
        bankAccountResponse.setBalance( bankAccount.getBalance() );
        bankAccountResponse.setUser( userToUserResponse( bankAccount.getUser() ) );

        return bankAccountResponse;
    }

    protected TypeResponse typeToTypeResponse(Type type) {
        if ( type == null ) {
            return null;
        }

        TypeResponse typeResponse = new TypeResponse();

        typeResponse.setId( type.getId() );
        typeResponse.setName( type.getName() );
        typeResponse.setDescription( type.getDescription() );

        return typeResponse;
    }
}
