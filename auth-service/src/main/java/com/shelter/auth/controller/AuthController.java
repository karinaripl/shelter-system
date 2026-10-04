package com.shelter.auth.controller;

import com.shelter.auth.dto.AuthRequest;
import com.shelter.auth.dto.AuthResponse;
import com.shelter.auth.dto.ChangePasswordRequest;
import com.shelter.auth.dto.RegisterRequest;
import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.service.AuthService;
import com.shelter.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Профиль текущего пользователя. userId выставляет gateway-auth-starter. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@RequestAttribute("userId") Long userId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
