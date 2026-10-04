package com.shelter.migrations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Одноразовое приложение для применения миграций Liquibase.
 *
 * <p>При старте Spring Boot автоматически выполняет changelog
 * {@code db/changelog/db.changelog-master.xml}, после чего приложение завершается
 * с кодом 0. Если миграция не прошла — код выхода ненулевой, и docker compose
 * не запустит сервисы, которые от неё зависят.
 */
@SpringBootApplication
public class MigrationsApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(MigrationsApplication.class, args)));
    }
}
