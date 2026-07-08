package com.ewallet.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over image storage. Implementations persist an uploaded file under
 * an opaque, random filename and can load it back as a {@link Resource}. The
 * active implementation is selected by {@code app.storage.type} (local | s3).
 */
public interface StorageService {

    /** Validates and stores the file, returning the generated opaque filename. */
    String store(MultipartFile file);

    /** Loads a previously stored file by its opaque filename. */
    Resource load(String filename);
}
