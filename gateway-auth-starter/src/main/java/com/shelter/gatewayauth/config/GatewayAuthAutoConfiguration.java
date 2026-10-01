package com.shelter.gatewayauth.config;

import com.shelter.gatewayauth.filter.GatewayAuthFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@AutoConfiguration
@EnableConfigurationProperties(GatewayAuthProperties.class)
@EnableMethodSecurity
public class GatewayAuthAutoConfiguration {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter(GatewayAuthProperties properties) {
        return new GatewayAuthFilter(properties);
    }

    @Bean
    public SecurityFilterChain gatewayAuthFilterChain(HttpSecurity http, GatewayAuthFilter gatewayAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}