package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.LocalUploadProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Controller
public class LocalUploadFileController {

    static final String URL_PREFIX = "/api/uploads/files/";

    private final LocalUploadProperties localUploadProperties;

    public LocalUploadFileController(LocalUploadProperties localUploadProperties) {
        this.localUploadProperties = localUploadProperties;
    }

    @GetMapping("/api/uploads/files/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        int index = uri.indexOf(URL_PREFIX);
        if (index < 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        String key = uri.substring(index + URL_PREFIX.length());
        if (key.isBlank() || key.contains("..") || key.contains("\\")) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        Path root = localUploadProperties.resolvedDir();
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        Resource resource;
        try {
            resource = new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资源不存在");
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(resource);
    }
}
