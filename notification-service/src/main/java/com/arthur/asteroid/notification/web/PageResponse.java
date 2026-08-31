package com.arthur.asteroid.notification.web;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A page of results, with a shape this service controls.
 *
 * <p>Spring Data's own {@code Page} is deliberately not returned from these
 * endpoints. Serialising {@code PageImpl} directly is explicitly unsupported - it
 * logs a warning and its JSON structure is not part of Spring Data's public
 * contract, so an upgrade can change the field names under a client that depends on
 * them. This record is four fields with no magic, and it is exactly what a paginated
 * view needs to draw a pager.
 */
public record PageResponse<T>(

        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(final Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
