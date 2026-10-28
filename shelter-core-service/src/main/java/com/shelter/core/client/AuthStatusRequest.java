package com.shelter.core.client;

/** Блокировка (enabled = false) и разблокировка учётной записи. */
public record AuthStatusRequest(boolean enabled) {
}
