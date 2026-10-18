package com.shelter.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Каталог для файлов фотографий животных (в Docker — volume shelter-photos). */
@ConfigurationProperties(prefix = "shelter.photos")
public record PhotoStorageProperties(String dir) {
}
