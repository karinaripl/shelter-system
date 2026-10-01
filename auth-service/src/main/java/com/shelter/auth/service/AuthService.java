package com.shelter.auth.service;

import com.shelter.auth.dto.AuthRequest;
import com.shelter.auth.dto.AuthResponse;
import com.shelter.auth.dto.RegisterRequest;
import com.shelter.auth.dto.UserRole;
import com.shelter.auth.exception.AuthException;
import com.shelter.auth.repository.UserRepository;
import com.shelter.model.generated.auth.tables.records.UsersRecord;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(RegisterRequest request) {
        UserRole role = request.role() != null ? request.role() : UserRole.USER;

        UsersRecord user = new UsersRecord();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role.name());

        UsersRecord saved = userRepository.save(user);

        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), saved.getRole());
        return new AuthResponse(token);
    }

    public AuthResponse login(AuthRequest request) {
        UsersRecord user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException("User not found"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("Invalid password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token);
    }
}