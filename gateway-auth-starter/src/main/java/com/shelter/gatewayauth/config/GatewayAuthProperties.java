package com.shelter.gatewayauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {
    private String secret;

    private List<String> internalPaths = List.of("/internal/**");

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public List<String> getInternalPaths() { return internalPaths; }
    public void setInternalPaths(List<String> internalPaths) { this.internalPaths = internalPaths; }
}
