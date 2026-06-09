package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.QueryResult;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface FileSourceService {

    /**
     * 生成xls
     * @param dataSource
     * @param data
     * @param response
     * @throws IOException
     */
    void writeSheet(DataSource dataSource, QueryResult data, HttpServletResponse response) throws IOException;

}
