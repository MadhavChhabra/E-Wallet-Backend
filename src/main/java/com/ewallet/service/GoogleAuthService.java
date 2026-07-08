package com.ewallet.service;

import com.ewallet.dto.response.JwtResponse;
import com.ewallet.model.Role;
import com.ewallet.model.RoleType;
import com.ewallet.model.User;
import com.ewallet.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Sign in with Google". The client obtains a Google ID token via the Google
 * Sign-In SDK and posts it here; the token is verified server-side against
 * Google's public keys (signature, expiry, and audience), and only then do we
 * find-or-create the local user and issue our own JWTs. The client never sends
 * a password and we never trust unverified claims.
 */
@Slf4j(topic = "GoogleAuthService")
@Service
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Value("${app.oauth.google.enabled:false}")
    private boolean enabled;

    @Value("${app.oauth.google.client-id:}")
    private String clientIds;

    private volatile GoogleIdTokenVerifier verifier;

    public GoogleAuthService(UserRepository userRepository,
                             RoleService roleService,
                             PasswordEncoder passwordEncoder,
                             AuthService authService) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional
    public JwtResponse loginWithGoogle(String idTokenString) {
        ensureConfigured();

        GoogleIdToken.Payload payload = verifyToken(idTokenString);

        String email = payload.getEmail();
        if (!StringUtils.hasText(email) || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseGet(() -> createUserFromGoogle(email.trim().toLowerCase(), payload));

        log.info("Google login for userId={} email={}", user.getId(), email);
        return authService.issueTokensForUser(user);
    }

    private GoogleIdToken.Payload verifyToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier().verify(idTokenString);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | java.io.IOException e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Could not verify Google ID token");
        }
    }

    private User createUserFromGoogle(String email, GoogleIdToken.Payload payload) {
        String firstName = defaultIfBlank((String) payload.get("given_name"), "Google");
        String lastName = defaultIfBlank((String) payload.get("family_name"), "User");

        User user = new User();
        user.setEmail(email);
        user.setUsername(generateUniqueUsername(email));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        // Federated accounts have no local password; set a random one so the
        // column is populated. They can set a password later via reset-by-OTP.
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        List<Role> roles = roleService.getReferenceByTypeIsIn(Set.of(RoleType.ROLE_USER));
        user.setRoles(new HashSet<>(roles));

        userRepository.save(user);
        log.info("Provisioned new user from Google login (username={})", user.getUsername());
        return user;
    }

    private String generateUniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@'))
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        if (base.length() < 3) {
            base = "user" + base;
        }
        if (base.length() > 16) {
            base = base.substring(0, 16);
        }
        String candidate = base;
        int suffix = 0;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            suffix++;
            candidate = base + suffix;
            if (candidate.length() > 20) {
                candidate = base.substring(0, 16) + suffix;
            }
        }
        return candidate;
    }

    private GoogleIdTokenVerifier verifier() {
        GoogleIdTokenVerifier local = verifier;
        if (local == null) {
            synchronized (this) {
                local = verifier;
                if (local == null) {
                    List<String> audiences = Arrays.stream(clientIds.split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .toList();
                    local = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                            .setAudience(audiences)
                            .build();
                    verifier = local;
                }
            }
        }
        return local;
    }

    private void ensureConfigured() {
        if (!enabled || !StringUtils.hasText(clientIds)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google login is not configured. Set GOOGLE_OAUTH_ENABLED=true and GOOGLE_CLIENT_ID.");
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
