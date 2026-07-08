package com.ewallet.service;

import com.ewallet.dto.response.RoleResponse;
import com.ewallet.dto.response.UserResponse;
import com.ewallet.exception.NoSuchElementFoundException;
import com.ewallet.model.User;
import com.ewallet.repository.RefreshTokenRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service used for User related operations
 */
@Slf4j(topic = "UserService")
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Fetches a single user reference (entity) by the given id
     *
     * @param id
     * @return User
     */
    public User getReferenceById(long id) {
        return userRepository.getReferenceById(id);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile() {
        return toResponse(loadCurrentUser());
    }

    @Transactional
    public UserResponse updateProfileImage(String filename) {
        User user = loadCurrentUser();
        user.setProfileImageFilename(filename);
        userRepository.save(user);
        log.info("Updated profile image for userId={}", user.getId());
        return toResponse(user);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User user = loadCurrentUser();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        int revoked = refreshTokenRepository.revokeAllForUser(user);
        log.info("Password changed for userId={} (revoked {} session(s))", user.getId(), revoked);
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserService.getCurrentUserId())
                .orElseThrow(() -> new NoSuchElementFoundException("User not found"));
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFirstName() + " " + user.getLastName());
        if (user.getProfileImageFilename() != null) {
            response.setProfileImageUrl("/api/v1/images/" + user.getProfileImageFilename());
        }
        Set<RoleResponse> roles = user.getRoles().stream()
                .map(role -> {
                    RoleResponse rr = new RoleResponse();
                    rr.setId(role.getId());
                    rr.setType(role.getType());
                    return rr;
                })
                .collect(Collectors.toSet());
        response.setRoles(roles);
        return response;
    }
}
