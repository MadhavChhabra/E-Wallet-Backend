package com.ewallet.controller;

import com.ewallet.dto.request.ChangePasswordRequest;
import com.ewallet.dto.request.ProfileImageRequest;
import com.ewallet.dto.response.ApiResponse;
import com.ewallet.dto.response.UserResponse;
import com.ewallet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;

import static com.ewallet.common.Constants.SUCCESS;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final Clock clock;
    private final UserService userService;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        UserResponse response = userService.getCurrentUserProfile();
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PutMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfileImage(
            @Valid @RequestBody ProfileImageRequest request) {
        UserResponse response = userService.updateProfileImage(request.getFilename());
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PutMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS));
    }
}
