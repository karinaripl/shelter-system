package com.shelter.auth.service;

import com.shelter.auth.dto.AuthRequest;
import com.shelter.auth.dto.AuthResponse;
import com.shelter.auth.dto.ChangePasswordRequest;
import com.shelter.auth.dto.RegisterRequest;
import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.dto.UserRole;
import com.shelter.auth.exception.InvalidCredentialsException;
import com.shelter.auth.exception.UserBlockedException;
import com.shelter.auth.exception.UserNotFoundException;
import com.shelter.auth.exception.WrongPasswordException;
import com.shelter.auth.repository.UserRepository;
import com.shelter.model.generated.auth.tables.records.UsersRecord;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       UserService userService,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Самостоятельная регистрация всегда даёт только роль VISITOR. */
    public AuthResponse register(RegisterRequest request) {
        UserResponse user = userService.createUser(
                request.username(), request.email(), request.password(), Set.of(UserRole.VISITOR));
        return new AuthResponse(jwtService.generateToken(user.id(), user.email(), user.roles()));
    }

    public AuthResponse login(AuthRequest request) {
        UsersRecord user = userRepository.findByEmail(UserService.normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new UserBlockedException();
        }

        Set<UserRole> roles = userRepository.findRoles(user.getId());
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getEmail(), roles));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        UsersRecord user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        userRepository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
    }
}
