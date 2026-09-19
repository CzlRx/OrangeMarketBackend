package com.czlr.orangemarketbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class LocalUploadProperties {

    @Value("${app.upload.local-dir:data/uploads}")
    private String localDir;

    public Path resolvedDir() {
        Path path = Path.of(localDir == null || localDir.isBlank() ? "data/uploads" : localDir.trim());
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }
}
