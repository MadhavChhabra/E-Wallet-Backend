package com.ewallet.controller;

import com.ewallet.dto.response.ApiResponse;
import com.ewallet.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.ewallet.common.Constants.SUCCESS;

/**
 * Image upload and retrieval. Upload requires authentication; retrieval is public
 * but only by the random, unguessable stored filename. Backed by {@link StorageService}
 * (local disk or S3, selected via {@code app.storage.type}).
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final Clock clock;
    private final StorageService storageService;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(@RequestParam("file") MultipartFile file) {
        String filename = storageService.store(file);
        Map<String, String> data = new HashMap<>();
        data.put("filename", filename);
        data.put("url", "/api/v1/images/" + filename);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, data));
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        Resource resource = storageService.load(filename);
        // Derive the content type from the filename extension so it works for any
        // backend (an S3 stream has no filename metadata for MediaTypeFactory).
        String contentType = MediaTypeFactory.getMediaType(filename)
                .map(MediaType::toString)
                .orElse("application/octet-stream");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(resource);
    }
}
