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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ewallet.common.Constants.*;

/**
 * Service used for Authentication related operations
 */
@Slf4j(topic = "AuthService")
@Service
@RequiredArgsConstructor
public class AuthService {

	private final AuthenticationManager authenticationManager;
	private final JwtUtils jwtUtils;
	private final UserRepository userRepository;
	private final SignupRequestMapper signupRequestMapper;
	private final RefreshTokenRepository refreshTokenRepo;
	


	/**
	 * Authenticates users by their credentials
	 *
	 * @param request
	 * @return JwtResponse
	 */
//    public JwtResponse login(LoginRequest request) {
//        final Authentication authentication = authenticationManager.authenticate(
//                new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword().trim()));
//
//        SecurityContextHolder.getContext().setAuthentication(authentication);
//        String jwt = jwtUtils.generateJwtToken(authentication);
//
//        final UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
//        final List<String> roles = userDetails.getAuthorities().stream()
//                .map(item -> item.getAuthority())
//                .toList();
//
//        log.info(LOGGED_IN_USER, new Object[]{request.getUsername()});
//        return JwtResponse
//                .builder()
//                .token(jwt)
//                .id(userDetails.getId())
//                .username(userDetails.getUsername())
//                .firstName(userDetails.getFirstName())
//                .lastName(userDetails.getLastName())
//                .roles(roles).build();
//    }
	public JwtResponse login(LoginRequest request) {
		try {
			final Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword().trim()));

			SecurityContextHolder.getContext().setAuthentication(authentication);

			// Generate access token
			String accessToken = jwtUtils.generateJwtToken(authentication);

			// Retrieve user details from authentication object
			final UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
			final List<String> roles = userDetails.getAuthorities().stream()
					.map(item -> item.getAuthority())
					.toList();

			// Retrieve user entity from repository
			User user = userRepository.findByUsername(request.getUsername())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found"));

			// Generate refresh token
			String refreshToken = jwtUtils.generateRefreshToken(authentication);

			// Save refresh token for the user
			saveUserRefreshToken(user, refreshToken);

			log.info("[AuthService:userSignInAuth] Access token for user: {} has been generated", userDetails.getUsername());

			// Build and return the response
			return JwtResponse.builder()
					.token(accessToken)
					.refreshToken(refreshToken)
					.id(userDetails.getId())
					.username(userDetails.getUsername())
					.firstName(userDetails.getFirstName())
					.lastName(userDetails.getLastName())
					.roles(roles).build();
		} catch (Exception e) {
			log.error("[AuthService:userSignInAuth] Exception while authenticating the user: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Please try again", e);
		}
	}


	/**
	 * Registers a user by provided credentials and user info
	 *
	 * @param request
	 * @return id of the registered user
	 */
	public CommandResponse signup(SignupRequest request) {
		if (userRepository.existsByUsernameIgnoreCase(request.getUsername().trim()))
			throw new ElementAlreadyExistsException(ALREADY_EXISTS_USER_NAME);
		if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim()))
			throw new ElementAlreadyExistsException(ALREADY_EXISTS_USER_EMAIL);

		final User user = signupRequestMapper.toEntity(request);
		userRepository.save(user);
		log.info(CREATED_USER, new Object[]{user.getUsername()});
		return CommandResponse.builder().id(user.getId()).build();
	}

	private void saveUserRefreshToken(User userInfoEntity, String refreshToken) {
		var refreshTokenEntity = RefreshToken.builder()
				.user(userInfoEntity)
				.refreshToken(refreshToken)
				.revoked(false)
				.build();
		refreshTokenRepo.save(refreshTokenEntity);
	}

	public Object getAccessTokenUsingRefreshToken(String authorizationHeader) {

//		if(!authorizationHeader.startsWith(TokenType.Bearer.name())){
//			return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Please verify your token type");
//		}

		final String refreshToken = authorizationHeader.substring(7);

		//Find refreshToken from database and should not be revoked : Same thing can be done through filter.
		var refreshTokenEntity = refreshTokenRepo.findByRefreshToken(refreshToken)
				.filter(tokens-> !tokens.isRevoked())
				.orElseThrow(()-> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Refresh token revoked"));

		User userInfoEntity = refreshTokenEntity.getUser();

		//Now create the Authentication object
		Authentication authentication =  createAuthenticationObject(userInfoEntity);

		//Use the authentication object to generate new accessToken as the Authentication object that we will have may not contain correct role.
		String accessToken = jwtUtils.generateJwtToken(authentication);

		return  JwtResponse.builder()
				.token(accessToken)
				.username(userInfoEntity.getUsername())
				.build();
	}

	private static Authentication createAuthenticationObject(User userInfoEntity) {
		// Extract user details from UserDetailsEntity
		String username = userInfoEntity.getEmail();
		String password = userInfoEntity.getPassword();
		Set<com.ewallet.model.Role> roles = userInfoEntity.getRoles();

		// Extract authorities from roles
		Set<GrantedAuthority> authorities = roles.stream()
				.map(role -> (GrantedAuthority) () -> role.getUsers().stream().findFirst().orElseThrow().getUsername()) // Assuming each role has only one user and User has a getUsername() method
				.collect(Collectors.toSet());

		return new UsernamePasswordAuthenticationToken(username, password, authorities);
	}
}


