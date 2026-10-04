package com.shelter.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Цепочку фильтров безопасности настраивает gateway-auth-starter:
 * публичные пути (регистрация, вход) заданы в gateway.auth.public-paths,
 * остальные запросы должны прийти через шлюз с подписанными заголовками.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
