package com.ewallet.service;

import com.ewallet.dto.mapper.SignupRequestMapper;
import com.ewallet.dto.request.LoginRequest;
import com.ewallet.dto.request.SignupRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.JwtResponse;
import com.ewallet.exception.ElementAlreadyExistsException;
import com.ewallet.model.RefreshToken;
import com.ewallet.model.User;
import com.ewallet.repository.RefreshTokenRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.CurrentUserService;
import com.ewallet.security.JwtUtils;
import com.ewallet.security.UserDetailsImpl;
import com.ewallet.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static com.ewallet.common.Constants.*;

@Slf4j(topic = "AuthService")
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final SignupRequestMapper signupRequestMapper;
    private final RefreshTokenRepository refreshTokenRepo;
    private final UserDetailsServiceImpl userDetailsService;
    private final CurrentUserService currentUserService;

    @Value("${app.security.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    @Transactional
    public JwtResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername().trim(),
                            request.getPassword().trim()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String accessToken = jwtUtils.generateJwtToken(authentication);
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(Object::toString)
                    .toList();

            User user = userRepository.findByUsername(request.getUsername().trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found"));

            String refreshToken = jwtUtils.generateRefreshToken(authentication);
            saveUserRefreshToken(user, refreshToken);

            log.info(LOGGED_IN_USER, userDetails.getUsername());

            return JwtResponse.builder()
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .id(userDetails.getId())
                    .username(userDetails.getUsername())
                    .firstName(userDetails.getFirstName())
                    .lastName(userDetails.getLastName())
                    .roles(roles)
                    .build();
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username: {}", request.getUsername());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }

    public CommandResponse signup(SignupRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername().trim())) {
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_USER_NAME);
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new ElementAlreadyExistsException(ALREADY_EXISTS_USER_EMAIL);
        }

        User user = signupRequestMapper.toEntity(request);
        userRepository.save(user);
        log.info(CREATED_USER, user.getUsername());
        return CommandResponse.builder().id(user.getId()).build();
    }

    /**
     * Issues a fresh access + refresh token pair for an already-authenticated
     * user (e.g. after a federated Google login). Mirrors what {@link #login}
     * returns so the client handles both paths identically.
     */
    @Transactional
    public JwtResponse issueTokensForUser(User user) {
        UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        String accessToken = jwtUtils.generateJwtToken(authentication);
        String refreshToken = jwtUtils.generateRefreshToken(authentication);
        saveUserRefreshToken(user, refreshToken);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(Object::toString)
                .toList();

        return JwtResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .firstName(userDetails.getFirstName())
                .lastName(userDetails.getLastName())
                .roles(roles)
                .build();
    }

    private void saveUserRefreshToken(User user, String refreshToken) {
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .refreshToken(refreshToken)
                .revoked(false)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .build();
        refreshTokenRepo.save(refreshTokenEntity);
    }

    /**
     * Exchanges a valid refresh token for a fresh access token. The refresh token
     * is rotated: the presented token is revoked and a new one is issued, so a
     * leaked token cannot be replayed after it has been used once.
     */
    @Transactional
    public JwtResponse getAccessTokenUsingRefreshToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization header must be Bearer token");
        }

        String refreshToken = authorizationHeader.substring(7);

        // Cryptographically verify the token before trusting the DB lookup.
        if (!jwtUtils.validateRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }

        RefreshToken refreshTokenEntity = refreshTokenRepo.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or revoked"));

        if (refreshTokenEntity.isRevoked() || refreshTokenEntity.isExpired()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or revoked");
        }

        User user = refreshTokenEntity.getUser();
        UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        // Rotate: revoke the used token and mint a new refresh token.
        refreshTokenEntity.setRevoked(true);
        refreshTokenRepo.save(refreshTokenEntity);

        String accessToken = jwtUtils.generateJwtToken(authentication);
        String newRefreshToken = jwtUtils.generateRefreshToken(authentication);
        saveUserRefreshToken(user, newRefreshToken);

        return JwtResponse.builder()
                .token(accessToken)
                .refreshToken(newRefreshToken)
                .id(userDetails.getId())
                .username(user.getUsername())
                .build();
    }

    /**
     * Revokes every active refresh token for the authenticated user. The stateless
     * access token remains valid until it expires (short-lived), but the session
     * can no longer be refreshed.
     */
    @Transactional
    public void logout() {
        Long currentUserId = currentUserService.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        int revoked = refreshTokenRepo.revokeAllForUser(user);
        log.info("User logged out (username: {}) - revoked {} refresh token(s)", user.getUsername(), revoked);
    }
}
