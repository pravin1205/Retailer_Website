package com.marketly.common.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wraps a Spring Data Page into our standard paginated response shape.
 */
@Getter
public class PageResponse<T> {

    private final List<T> data;
    private final PaginationMeta pagination;

    private PageResponse(List<T> data, PaginationMeta pagination) {
        this.data = data;
        this.pagination = pagination;
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        PaginationMeta meta = new PaginationMeta(
            page.getNumber() + 1,       // convert 0-indexed to 1-indexed
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
        return new PageResponse<>(page.getContent(), meta);
    }

    @Getter
    public static class PaginationMeta {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final boolean hasNext;
        private final boolean hasPrevious;

        public PaginationMeta(int page, int size, long totalElements,
                              int totalPages, boolean hasNext, boolean hasPrevious) {
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
            this.hasNext = hasNext;
            this.hasPrevious = hasPrevious;
        }
    }
}
