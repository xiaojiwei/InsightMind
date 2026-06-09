package com.graphinsight.indicator.service;

import javax.servlet.http.HttpServletResponse;

public interface BosFileService {

    void downloadBosFile(HttpServletResponse response, String fileName) throws Exception;
    void downloadBosFile(HttpServletResponse response, String fileName, String downloadid) throws Exception;
}
