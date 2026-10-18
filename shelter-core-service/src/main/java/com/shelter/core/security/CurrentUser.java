package com.shelter.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Текущий пользователь: id и роли из подписанных заголовков шлюза (их разбирает gateway-auth-starter). */
@Component
public class CurrentUser {

    public Long id() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object userId = attributes != null
                ? attributes.getAttribute("userId", RequestAttributes.SCOPE_REQUEST)
                : null;
        return userId instanceof Long id ? id : null;
    }

    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
