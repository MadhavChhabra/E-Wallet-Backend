package com.ewallet.repository;

import com.ewallet.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String name);

    boolean existsByEmailIgnoreCase(String email);
}
