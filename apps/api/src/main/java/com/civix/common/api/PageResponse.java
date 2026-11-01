package com.civix.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Pagination envelope: {@code {items, page, size, totalItems, totalPages}}. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size == 0 ? 0 : (int) ((totalItems + size - 1) / size);
        return new PageResponse<>(List.copyOf(items), page, size, totalItems, totalPages);
    }
}
