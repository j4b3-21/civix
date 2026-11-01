package com.civix.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Normalises {@code ?page&size} (size defaults to 20, max 100). */
public final class Pagination {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Pagination() {
    }

    public static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public static int size(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        return PageRequest.of(page(page), size(size), sort);
    }

    public static Pageable newestFirst(Integer page, Integer size, String property) {
        return of(page, size, Sort.by(Sort.Direction.DESC, property));
    }
}
