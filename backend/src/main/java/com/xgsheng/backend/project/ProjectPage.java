package com.xgsheng.backend.project;

import com.xgsheng.backend.exception.PendingProductRuleException;

public final class ProjectPage {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private final int page;
    private final int pageSize;

    private ProjectPage(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }

    public static ProjectPage resolve(Integer page, Integer pageSize) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPage < 1 || resolvedSize < 1 || resolvedSize > MAX_PAGE_SIZE) {
            throw new PendingProductRuleException(
                    "project list page bounds are not confirmed: page=" + resolvedPage + ", pageSize=" + resolvedSize);
        }
        return new ProjectPage(resolvedPage, resolvedSize);
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long offset() {
        return (page - 1L) * pageSize;
    }
}
