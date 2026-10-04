package com.shelter.auth.service;

import com.shelter.auth.dto.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /** Роли кладутся в claim "roles" списком, например ["ADMIN", "VET"]. */
    public String generateToken(Long userId, String email, Collection<UserRole> roles) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        List<String> roleNames = roles.stream().sorted().map(UserRole::name).toList();

        return Jwts.builder()
                .subject(email)
                .claims(Map.of("roles", roleNames, "userId", userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                // Алгоритм задаём явно: иначе jjwt выбирает его по длине ключа
                // (≥48 байт → HS384), а шлюз проверяет токены только по HS256.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
