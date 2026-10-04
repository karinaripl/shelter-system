package com.shelter.auth.service;

import com.shelter.auth.dto.AuthRequest;
import com.shelter.auth.dto.RegisterRequest;
import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.dto.UserRole;
import com.shelter.auth.exception.InvalidCredentialsException;
import com.shelter.auth.exception.UserBlockedException;
import com.shelter.auth.repository.UserRepository;
import com.shelter.model.generated.auth.tables.records.UsersRecord;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789";

    private UserRepository userRepository;
    private UserService userService;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = mock(UserService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 60_000L);

        authService = new AuthService(userRepository, userService, jwtService, passwordEncoder);
    }

    @Test
    void registrationGivesOnlyVisitorRole() {
        when(userService.createUser("guest", "guest@shelter.local", "password1", Set.of(UserRole.VISITOR)))
                .thenReturn(new UserResponse(7L, "guest", "guest@shelter.local",
                        List.of(UserRole.VISITOR), true, null));

        String token = authService.register(
                new RegisterRequest("guest", "guest@shelter.local", "password1")).token();

        verify(userService).createUser("guest", "guest@shelter.local", "password1", Set.of(UserRole.VISITOR));
        assertThat(claims(token).get("roles", List.class)).containsExactly("VISITOR");
    }

    @Test
    void tokenContainsAllRolesAsList() {
        when(userRepository.findByEmail("doc@shelter.local")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("password1", "hash")).thenReturn(true);
        when(userRepository.findRoles(3L)).thenReturn(EnumSet.of(UserRole.VET, UserRole.ADMIN));

        Claims claims = claims(authService.login(new AuthRequest("Doc@Shelter.local", "password1")).token());

        assertThat(claims.getSubject()).isEqualTo("doc@shelter.local");
        assertThat(claims.get("userId", Long.class)).isEqualTo(3L);
        assertThat(claims.get("roles", List.class)).containsExactly("ADMIN", "VET");
    }

    @Test
    void unknownEmailAndWrongPasswordGiveSameError() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new AuthRequest("nobody@shelter.local", "password1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Неверный email или пароль");

        when(userRepository.findByEmail("doc@shelter.local")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("wrong-pass", "hash")).thenReturn(false);
        assertThatThrownBy(() -> authService.login(new AuthRequest("doc@shelter.local", "wrong-pass")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Неверный email или пароль");
    }

    @Test
    void blockedUserCannotLogIn() {
        when(userRepository.findByEmail("doc@shelter.local")).thenReturn(Optional.of(user(false)));
        when(passwordEncoder.matches("password1", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new AuthRequest("doc@shelter.local", "password1")))
                .isInstanceOf(UserBlockedException.class);
    }

    private static UsersRecord user(boolean enabled) {
        UsersRecord record = new UsersRecord();
        record.setId(3L);
        record.setUsername("doc");
        record.setEmail("doc@shelter.local");
        record.setPasswordHash("hash");
        record.setEnabled(enabled);
        return record;
    }

    private static Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
