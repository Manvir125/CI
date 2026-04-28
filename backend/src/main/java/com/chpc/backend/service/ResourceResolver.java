package com.chpc.backend.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceResolver {

    private ResourceResolver() {
    }

    public static InputStream open(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("Ruta de recurso vacia");
        }

        Resource resource = new ClassPathResource(location);
        if (resource.exists()) {
            return resource.getInputStream();
        }

        Path path = Path.of(location);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }

        Resource fileSystemResource = new FileSystemResource(location);
        if (fileSystemResource.exists()) {
            return fileSystemResource.getInputStream();
        }

        throw new IOException("No se encontro el recurso: " + location);
    }
}
