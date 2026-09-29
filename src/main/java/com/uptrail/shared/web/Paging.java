package com.uptrail.shared.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Page requests and pagination links. Users choose 10, 20 or 25 rows per page; the server never serves more
 * than 50 rows whatever the request says. Links keep every other query parameter (the filters).
 */
public final class Paging {

    public static final List<Integer> PAGE_SIZES = List.of(10, 20, 25);
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    private Paging() {
    }

    /** @param page one-based page number from the query string */
    public static Pageable request(Integer page, Integer size) {
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int safePage = page == null || page < 1 ? 0 : page - 1;
        return PageRequest.of(safePage, safeSize);
    }

    public record Param(String name, String value) {
    }

    public record View<T>(List<T> content, int page, int size, long totalElements, int totalPages, String path,
            List<Param> params, List<Integer> pageNumbers) {

        public boolean isEmpty() {
            return content.isEmpty();
        }

        public boolean hasPrevious() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages;
        }

        public long firstRow() {
            return totalElements == 0 ? 0 : (long) (page - 1) * size + 1;
        }

        public long lastRow() {
            return Math.min((long) page * size, totalElements);
        }

        public int lastPageNumber() {
            return pageNumbers.get(pageNumbers.size() - 1);
        }

        public String url(int targetPage) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
            params.forEach(p -> builder.queryParam(p.name(), p.value()));
            return builder.queryParam("page", targetPage).queryParam("size", size).encode().build().toUriString();
        }

        public List<Integer> sizes() {
            return PAGE_SIZES;
        }
    }

    public static <E, T> View<T> view(Page<E> page, Function<E, T> mapper, HttpServletRequest request) {
        return view(page.map(mapper), request);
    }

    public static <T> View<T> view(Page<T> page, HttpServletRequest request) {
        List<Param> params = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String name = entry.getKey();
            if (name.equals("page") || name.equals("size") || name.startsWith("_")) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (value != null && !value.isBlank()) {
                    params.add(new Param(name, value));
                }
            }
        }
        int current = page.getNumber() + 1;
        int total = Math.max(page.getTotalPages(), 1);
        List<Integer> numbers = new ArrayList<>();
        for (int n = Math.max(1, current - 2); n <= Math.min(total, current + 2); n++) {
            numbers.add(n);
        }
        return new View<>(page.getContent(), current, page.getSize(), page.getTotalElements(), total,
                request.getRequestURI(), List.copyOf(params), numbers);
    }

    /** A page of an already computed list (for grouped views that paginate by group). */
    public static <T> Page<T> slice(List<T> all, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }
}
