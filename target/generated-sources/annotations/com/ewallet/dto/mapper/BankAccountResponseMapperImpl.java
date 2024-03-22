package com.ewallet.dto.mapper;

import com.ewallet.dto.response.BankAccountResponse;
import com.ewallet.dto.response.RoleResponse;
import com.ewallet.dto.response.UserResponse;
import com.ewallet.model.BankAccount;
import com.ewallet.model.Role;
import com.ewallet.model.User;
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
public class BankAccountResponseMapperImpl implements BankAccountResponseMapper {

    @Override
    public BankAccount toEntity(BankAccountResponse dto) {
        if ( dto == null ) {
            return null;
        }

        BankAccount bankAccount = new BankAccount();

        bankAccount.setId( dto.getId() );
        bankAccount.setIban( dto.getIban() );
        bankAccount.setName( dto.getName() );
        bankAccount.setBalance( dto.getBalance() );
        bankAccount.setUser( userResponseToUser( dto.getUser() ) );

        return bankAccount;
    }

    @Override
    public BankAccountResponse toDto(BankAccount entity) {
        if ( entity == null ) {
            return null;
        }

        BankAccountResponse bankAccountResponse = new BankAccountResponse();

        bankAccountResponse.setId( entity.getId() );
        bankAccountResponse.setIban( entity.getIban() );
        bankAccountResponse.setName( entity.getName() );
        bankAccountResponse.setBalance( entity.getBalance() );
        bankAccountResponse.setUser( userToUserResponse( entity.getUser() ) );

        return bankAccountResponse;
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

        setFullName( userResponse, user );

        return userResponse;
    }
}
