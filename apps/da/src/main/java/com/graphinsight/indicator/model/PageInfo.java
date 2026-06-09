package com.graphinsight.indicator.model;

import lombok.Data;

@Data
public class PageInfo {

    int totalRows = 0;
    int pageRecorders = 20;
    int totalPages = 0;
    int pageStartRow = 0;
    int pageEndRow = 0;
    int currentPage = 1;
    boolean hasNextPage = false;
    boolean hasPreviousPage = false;

    String queryCountId = null;

    public void setTotalRows(int totalRows) {

        //前端分页默认按20页处理
//        if (totalRows > 200) {
//            totalRows = 200;
//        }

        this.totalRows = totalRows;
    }

    public void calc() {
        if ((totalRows % pageRecorders) == 0) {
            totalPages = totalRows / pageRecorders;
        } else {
            totalPages = totalRows / pageRecorders + 1;
        }
        totalPages = (totalPages == 0) ? 1 : totalPages;

//        if (totalPages > 20) {
//            totalPages = 20;
//        }

        if (totalRows < pageRecorders) {
            this.pageStartRow = 0;
            this.pageEndRow = totalRows;
        } else {
            this.pageStartRow = 0;
            this.pageEndRow = pageRecorders;
        }

        if (currentPage >= totalPages) {
            hasNextPage = false;
        } else {
            hasNextPage = true;
        }
    }

    public PageInfo(int pageRecorders) {
        this.pageRecorders = pageRecorders;
    }

    public void calcRange(int page) {

        currentPage = page;
        if (currentPage <= 0) {
            currentPage = 1;
        }

        if (currentPage >= totalPages) {
            currentPage = totalPages;
            hasNextPage = false;
        } else {
            hasNextPage = true;
        }

        if (currentPage > 1) {
            hasPreviousPage = true;
        } else {
            hasPreviousPage = false;
        }

        if (currentPage * pageRecorders < totalRows) {
            pageEndRow = currentPage * pageRecorders;
            pageStartRow = pageEndRow - pageRecorders;
        } else {
            pageEndRow = totalRows;
            pageStartRow = pageRecorders * (totalPages - 1);
        }

    }

}
