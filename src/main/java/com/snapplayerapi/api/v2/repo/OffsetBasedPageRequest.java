package com.snapplayerapi.api.v2.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Offset-based {@link Pageable} implementation for APIs that use raw offset+limit pagination
 * instead of Spring Data's default page-number model.
 *
 * <p>Spring Data's {@code PageRequest} uses zero-based page numbers where offset = page * size.
 * This class allows arbitrary offsets (e.g. offset=15, limit=10) that don't align with a fixed
 * page size, which matches the contract of the v2 API ({@code offset}, {@code limit} params).</p>
 *
 * <p>When used with {@code Slice<T>} queries, Spring Data fetches {@code limit + 1} rows to
 * determine {@code hasNext()}, making count queries unnecessary.</p>
 */
public class OffsetBasedPageRequest implements Pageable {

    private final long offset;
    private final int limit;
    private final Sort sort;

    public OffsetBasedPageRequest(int offset, int limit, Sort sort) {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        this.offset = offset;
        this.limit = limit;
        this.sort = sort != null ? sort : Sort.unsorted();
    }

    @Override
    public int getPageNumber() {
        // Approximate page number — used by Spring Data internally but not meaningful for offset pagination
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetBasedPageRequest((int) (offset + limit), limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious()
                ? new OffsetBasedPageRequest((int) Math.max(0, offset - limit), limit, sort)
                : first();
    }

    @Override
    public Pageable first() {
        return new OffsetBasedPageRequest(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetBasedPageRequest(pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
