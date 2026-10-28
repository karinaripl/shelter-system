package com.shelter.core.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Feign-клиенты других сервисов. Отдельный класс, а не аннотация на приложении: срезы тестов (@WebMvcTest)
 * не подхватывают @Configuration и не пытаются создать клиентов.
 */
@Configuration
@EnableFeignClients(basePackages = "com.shelter.core.client")
public class FeignConfig {
}
