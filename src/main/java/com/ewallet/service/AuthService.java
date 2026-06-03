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
import com.ewallet.security.JwtUtils;
import com.ewallet.security.UserDetailsImpl;
import com.ewallet.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    private void saveUserRefreshToken(User user, String refreshToken) {
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .refreshToken(refreshToken)
                .revoked(false)
                .build();
        refreshTokenRepo.save(refreshTokenEntity);
    }

    public JwtResponse getAccessTokenUsingRefreshToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization header must be Bearer token");
        }

        String refreshToken = authorizationHeader.substring(7);

        RefreshToken refreshTokenEntity = refreshTokenRepo.findByRefreshToken(refreshToken)
                .filter(token -> !token.isRevoked())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or revoked"));

        User user = refreshTokenEntity.getUser();
        UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        String accessToken = jwtUtils.generateJwtToken(authentication);

        return JwtResponse.builder()
                .token(accessToken)
                .username(user.getUsername())
                .build();
    }
}
