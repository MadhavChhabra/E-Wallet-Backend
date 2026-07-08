package com.ewallet.config;

import com.ewallet.model.Role;
import com.ewallet.model.RoleType;
import com.ewallet.model.Type;
import com.ewallet.repository.RoleRepository;
import com.ewallet.repository.TypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds reference data (roles and transaction types) that the application needs
 * to function. Runs on every startup but is fully idempotent — it only inserts
 * rows that are missing, so it is safe with {@code ddl-auto=update} in dev and
 * with a pre-migrated schema in prod.
 *
 * <p>Transaction types are seeded in a stable order so that "Transfer" keeps
 * id 1 on a fresh database, matching existing references.
 */
@Slf4j(topic = "DataInitializer")
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** Ordered so the first insert on a fresh DB gets id 1. */
    static final List<String> DEFAULT_TYPES = List.of(
            "Transfer",
            "Deposit",
            "Withdraw",
            "Top Up",
            "Payment");

    private final RoleRepository roleRepository;
    private final TypeRepository typeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedTypes();
    }

    private void seedRoles() {
        Set<RoleType> existing = EnumSet.noneOf(RoleType.class);
        roleRepository.findAll().forEach(role -> existing.add(role.getType()));

        Arrays.stream(RoleType.values())
                .filter(type -> !existing.contains(type))
                .forEach(type -> {
                    Role role = new Role();
                    role.setType(type);
                    roleRepository.save(role);
                    log.info("Seeded role {}", type);
                });
    }

    private void seedTypes() {
        DEFAULT_TYPES.stream()
                .filter(name -> !typeRepository.existsByNameIgnoreCase(name))
                .forEach(name -> {
                    Type type = new Type();
                    type.setName(name);
                    typeRepository.save(type);
                    log.info("Seeded transaction type {}", name);
                });
    }
}
