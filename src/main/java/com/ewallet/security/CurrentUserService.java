package com.ewallet.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public UserDetailsImpl getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl user)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return user;
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public void assertUserIdMatchesCurrentUser(Long userId) {
        if (!getCurrentUserId().equals(userId)) {
            throw new AccessDeniedException("You do not have access to this resource");
        }
    }
}
