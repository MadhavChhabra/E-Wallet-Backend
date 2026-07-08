package com.ewallet.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/**
 * Shared upload validation and filename generation for all storage backends.
 * Subclasses only implement the actual {@code write}/{@code read}.
 */
public abstract class AbstractStorageService implements StorageService {

    protected static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    protected static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final long maxFileSizeBytes;

    protected AbstractStorageService(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum allowed size");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only image files are allowed");
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported image extension");
        }

        String filename = UUID.randomUUID() + "." + ext;
        write(filename, file);
        return filename;
    }

    @Override
    public Resource load(String filename) {
        return read(sanitize(filename));
    }

    protected abstract void write(String filename, MultipartFile file);

    protected abstract Resource read(String filename);

    /** Rejects path traversal / directory separators in a stored filename. */
    protected String sanitize(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "" : filename);
        if (cleaned.isEmpty() || cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        return cleaned;
    }

    private String extractExtension(String originalName) {
        String ext = StringUtils.getFilenameExtension(originalName == null ? "" : originalName);
        return ext == null ? "" : ext.toLowerCase();
    }
}
