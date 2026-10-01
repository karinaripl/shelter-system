package com.shelter.auth.dto;

public record RegisterRequest(String username, String email, String password, UserRole role) {}