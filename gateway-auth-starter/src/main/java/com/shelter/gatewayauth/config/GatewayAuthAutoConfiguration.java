package com.shelter.gatewayauth.config;

import com.shelter.gatewayauth.error.SecurityErrorWriter;
import com.shelter.gatewayauth.filter.GatewayAuthFilter;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;
import java.util.stream.Stream;

/**
 * Безопасность сервиса за шлюзом. Стартер сам включает web security: цепочка фильтров ниже
 * не должна зависеть от того, подключена ли автоконфигурация Spring Boot (например, в @WebMvcTest).
 */
@AutoConfiguration
@EnableConfigurationProperties(GatewayAuthProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class GatewayAuthAutoConfiguration {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter(GatewayAuthProperties properties) {
        return new GatewayAuthFilter(properties);
    }

    /**
     * Фильтр работает только внутри цепочки Spring Security. Без этого Spring Boot зарегистрировал бы
     * его ещё и как обычный servlet-фильтр: если тот сработает раньше цепочки, цепочка заменит контекст
     * безопасности пустым, а повторный вызов внутри цепочки будет пропущен — запрос станет анонимным.
     */
    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> gatewayAuthFilterRegistration(GatewayAuthFilter filter) {
        FilterRegistrationBean<GatewayAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain gatewayAuthFilterChain(HttpSecurity http,
                                                      GatewayAuthFilter gatewayAuthFilter,
                                                      GatewayAuthProperties properties) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Админ-панель (сотрудники, деньги): только ADMIN — вторая проверка после шлюза
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(permittedPaths(properties)).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> SecurityErrorWriter.write(
                                request, response, HttpStatus.UNAUTHORIZED, SecurityErrorWriter.UNAUTHORIZED_MESSAGE))
                        .accessDeniedHandler((request, response, e) -> SecurityErrorWriter.write(
                                request, response, HttpStatus.FORBIDDEN, SecurityErrorWriter.FORBIDDEN_MESSAGE))
                )
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static String[] permittedPaths(GatewayAuthProperties properties) {
        return Stream.of(properties.getInternalPaths(), properties.getPublicPaths(), List.of("/error"))
                .flatMap(List::stream)
                .toArray(String[]::new);
    }
}