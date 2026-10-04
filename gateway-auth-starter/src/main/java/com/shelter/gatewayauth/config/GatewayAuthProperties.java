package com.shelter.gatewayauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {
    private String secret;

    /** Вызовы между сервисами: заголовки шлюза не требуются. */
    private List<String> internalPaths = List.of("/internal/**");

    /**
     * Публичные пути (например, регистрация и вход): доступны без заголовков шлюза,
     * но если заголовки пришли, они проверяются как обычно.
     */
    private List<String> publicPaths = List.of();

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public List<String> getInternalPaths() { return internalPaths; }
    public void setInternalPaths(List<String> internalPaths) { this.internalPaths = internalPaths; }

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }
}
