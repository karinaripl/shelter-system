package com.shelter.core.repository;

/** Товар: название и единица измерения — для норм расхода и сообщений. */
public record ProductInfo(Long id, String name, String unit, boolean active) {
}
