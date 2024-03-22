package com.ewallet.security;

import com.ewallet.repository.RefreshTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.ewallet.common.Constants.CANNOT_SET_AUTH;

/**
 * Makes a single execution for each request to our API. It provides a doFilterInternal() method
 * that we will implement parsing & validating Refresh Token, loading User details,
 * and checking Authorization.
 */
@Slf4j(topic = "RefreshTokenFilter")
public class RefreshTokenFilter extends OncePerRequestFilter {

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserDetailsServiceImpl userDetailsService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			String refreshToken = parseRefreshToken(request);
			if (refreshToken != null && refreshTokenRepository.existsByRefreshToken(refreshToken)) {
				final String username = refreshTokenRepository.getUsernameFromRefreshToken(refreshToken);

				final UserDetails userDetails = userDetailsService.loadUserByUsername(username);
				final UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(
								userDetails,
								null,
								userDetails.getAuthorities());
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		} catch (Exception e) {
			log.error(CANNOT_SET_AUTH, e);
		}
		filterChain.doFilter(request, response);
	}

	private String parseRefreshToken(HttpServletRequest request) {
		String headerAuth = request.getHeader("Authorization");

		if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
			return headerAuth.substring(7); // Assuming the token starts after "Bearer "
		}
		return null;
	}
}
