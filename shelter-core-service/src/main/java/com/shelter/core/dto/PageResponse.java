package com.shelter.core.dto;

import java.util.List;

/** Страница списка. page — номер с нуля. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
