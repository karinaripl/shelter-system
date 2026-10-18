package com.shelter.core.service;

import com.shelter.core.exception.InvalidRequestException;

/** Проверка параметров страницы: page — с нуля, size — от 1 до 100. */
final class Paging {

    static final int MAX_SIZE = 100;

    private Paging() {
    }

    static void validate(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("Номер страницы (page) не может быть отрицательным");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("Размер страницы (size) — от 1 до " + MAX_SIZE);
        }
    }
}
