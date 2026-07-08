package com.ewallet.service.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Default storage backend: writes to the local filesystem. Active unless
 * {@code app.storage.type=s3}.
 */
@Slf4j(topic = "LocalStorageService")
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService extends AbstractStorageService {

    private final Path root;

    public LocalStorageService(
            @Value("${app.storage.location:./data/uploads}") String location,
            @Value("${app.storage.max-file-size-bytes:5242880}") long maxFileSizeBytes) {
        super(maxFileSizeBytes);
        this.root = Paths.get(location).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            log.info("Local file storage initialized at {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize storage directory: " + root, e);
        }
    }

    @Override
    protected void write(String filename, MultipartFile file) {
        Path destination = root.resolve(filename).normalize();
        if (!destination.getParent().equals(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }
        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store file");
        }
    }

    @Override
    protected Resource read(String filename) {
        Path filePath = root.resolve(filename).normalize();
        if (!filePath.getParent().equals(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
    }
}
