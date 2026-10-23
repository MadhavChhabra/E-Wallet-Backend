package com.ewallet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ewallet.exception.RateLimitExceededException;
import com.ewallet.service.RateLimitService;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        // Password reset shares the login bucket: a 6-digit code is only as strong as the
        // number of guesses allowed against it.
        boolean isRateLimited = "POST".equalsIgnoreCase(request.getMethod())
                && (uri.contains("/api/v1/auth/login") || uri.contains("/api/v1/auth/demo")
                || uri.contains("/api/v1/auth/password-reset/"));
        if (isRateLimited) {
            try {
                rateLimitService.checkLoginLimit(resolveClientKey(request));
            } catch (RateLimitExceededException ex) {
                writeRateLimitResponse(response, ex);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, RateLimitExceededException ex)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":429,"message":"%s","errorCode":"RATE_LIMIT_EXCEEDED"}
                """.formatted(ex.getMessage()));
    }
}
