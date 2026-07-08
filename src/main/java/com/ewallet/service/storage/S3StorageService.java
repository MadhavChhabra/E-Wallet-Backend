package com.ewallet.service.storage;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

/**
 * S3-backed storage. Active when {@code app.storage.type=s3}. Credentials are
 * resolved from the default AWS provider chain (environment variables locally,
 * or the EC2 instance role in production) — never hard-coded.
 */
@Slf4j(topic = "S3StorageService")
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService extends AbstractStorageService {

    private final S3Client s3;
    private final String bucket;

    public S3StorageService(
            @Value("${app.storage.max-file-size-bytes:5242880}") long maxFileSizeBytes,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.region:ap-south-1}") String region) {
        super(maxFileSizeBytes);
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("app.storage.type=s3 requires app.storage.s3.bucket to be set");
        }
        this.bucket = bucket;
        this.s3 = S3Client.builder().region(Region.of(region)).build();
        log.info("S3 file storage initialized (bucket={}, region={})", bucket, region);
    }

    @Override
    protected void write(String filename, MultipartFile file) {
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(filename)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            log.error("Failed to upload to S3: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store file");
        }
    }

    @Override
    protected Resource read(String filename) {
        try {
            return new InputStreamResource(
                    s3.getObject(GetObjectRequest.builder().bucket(bucket).key(filename).build()));
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
    }

    @PreDestroy
    void close() {
        s3.close();
    }
}
