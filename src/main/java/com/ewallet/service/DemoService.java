package com.ewallet.service;

import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.JwtResponse;
import com.ewallet.model.Role;
import com.ewallet.model.RoleType;
import com.ewallet.model.User;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.UserDetailsImpl;
import com.ewallet.validator.IbanGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Provisions a fresh, pre-populated demo account so a first-time visitor lands on
 * a filled dashboard (accounts with balances + realistic transaction history)
 * instead of an empty one. Each call creates an isolated user, so concurrent
 * demo visitors never interfere with each other.
 *
 * <p>Seeding runs through the real services (create account, transfer, deposit,
 * withdraw) under a temporary security context, so the data is authentic —
 * balances reconcile and transactions are genuine.
 */
@Slf4j(topic = "DemoService")
@Service
public class DemoService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final BankAccountService bankAccountService;
    private final AuthService authService;

    @Value("${app.demo.enabled:true}")
    private boolean enabled;

    public DemoService(UserRepository userRepository,
                       RoleService roleService,
                       PasswordEncoder passwordEncoder,
                       BankAccountService bankAccountService,
                       AuthService authService) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.bankAccountService = bankAccountService;
        this.authService = authService;
    }

    @Transactional
    public JwtResponse createDemoSession() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo mode is disabled");
        }

        User user = provisionUser();

        // Seed data as the demo user, then always clear the context.
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            seedData(user);
        } finally {
            SecurityContextHolder.clearContext();
        }

        log.info("Provisioned demo session for userId={} username={}", user.getId(), user.getUsername());
        return authService.issueTokensForUser(user);
    }

    private User provisionUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User user = new User();
        user.setUsername("demo_" + suffix);
        user.setEmail("demo_" + suffix + "@ewallet.demo");
        user.setFirstName("Demo");
        user.setLastName("User");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        Set<Role> roles = new HashSet<>(roleService.getReferenceByTypeIsIn(Set.of(RoleType.ROLE_USER)));
        user.setRoles(roles);
        return userRepository.save(user);
    }

    private void seedData(User user) {
        String everydayIban = generateIban();
        String savingsIban = generateIban();

        createAccount(user.getId(), everydayIban, "Everyday", "2500.00");
        createAccount(user.getId(), savingsIban, "Savings", "8000.00");

        // A realistic little history.
        transfer(everydayIban, savingsIban, "500.00");
        deposit(everydayIban, "1000.00");
        withdraw(everydayIban, "300.00");
        transfer(savingsIban, everydayIban, "250.00");
    }

    private void createAccount(Long userId, String iban, String name, String balance) {
        BankAccountRequest req = new BankAccountRequest();
        req.setUserId(userId);
        req.setIban(iban);
        req.setName(name);
        bankAccountService.createWithOpeningBalance(req, new java.math.BigDecimal(balance));
    }

    private void transfer(String fromIban, String toIban, String amount) {
        bankAccountService.transferFunds(txRequest(fromIban, toIban, amount, "Demo transfer"), null);
    }

    private void deposit(String toIban, String amount) {
        TransactionRequest req = txRequest(toIban, toIban, amount, "Demo deposit");
        bankAccountService.addFunds(req, null);
    }

    private void withdraw(String fromIban, String amount) {
        TransactionRequest req = txRequest(fromIban, fromIban, amount, "Demo withdrawal");
        bankAccountService.withdrawFunds(req, null);
    }

    private TransactionRequest txRequest(String fromIban, String toIban, String amount, String description) {
        TransactionRequest req = new TransactionRequest();
        req.setFromBankAccountIban(fromIban);
        req.setToBankAccountIban(toIban);
        req.setAmount(new java.math.BigDecimal(amount));
        req.setDescription(description);
        req.setTypeId(1L); // "Transfer" reference type (seeded first)
        return req;
    }

    private String generateIban() {
        return IbanGenerator.generate();
    }
}
