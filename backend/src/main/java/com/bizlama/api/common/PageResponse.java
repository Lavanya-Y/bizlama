package com.bizlama.api.common;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static <T> PageResponse<T> of(
            List<T> items,
            int page,
            int size,
            long totalItems
    ) {
        int pages = totalItems == 0
                ? 0
                : (int) Math.ceil((double) totalItems / size);

        return new PageResponse<>(items, page, size, totalItems, pages);
    }
}