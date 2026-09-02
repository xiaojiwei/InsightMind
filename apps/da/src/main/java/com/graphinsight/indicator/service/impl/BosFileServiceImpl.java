package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.service.BosFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class BosFileServiceImpl implements BosFileService {

    public static String writeBos(String fileName) {
        log.info("file export retained locally: {}", fileName);
        return fileName;
    }

    @Override
    public void downloadBosFile(HttpServletResponse response, String fileName) throws IOException {
        streamLocalFile(response, fileName);
    }

    @Override
    public void downloadBosFile(HttpServletResponse response, String fileName, String downloadid) throws IOException {
        streamLocalFile(response, fileName);
    }

    private void streamLocalFile(HttpServletResponse response, String fileName) throws IOException {
        Path filePath = Paths.get(fileName).normalize();
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.warn("local export file not found: {}", fileName);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"file not found in local export storage\"}");
            return;
        }

        String encodedName = URLEncoder.encode(filePath.getFileName().toString(), "UTF-8");
        String contentType = Files.probeContentType(filePath);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"");
        response.setContentType(contentType == null ? "application/octet-stream" : contentType);
        Files.copy(filePath, response.getOutputStream());
        response.flushBuffer();
    }
}
