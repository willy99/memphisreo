package com.memphisreo.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

/**
 * Бакет перевіряється/створюється ледаче, при першому записі: старт
 * застосунку не залежить від доступності сховища.
 */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private volatile boolean bucketVerified;

    public S3ObjectStorage(S3Client s3Client, S3Presigner s3Presigner,
                            @Value("${memphisreo.storage.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    private void ensureBucketExists() {
        if (bucketVerified) {
            return;
        }
        synchronized (this) {
            if (bucketVerified) {
                return;
            }
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            }
            bucketVerified = true;
        }
    }

    @Override
    public String put(String key, InputStream content, long contentLength, String contentType) {
        ensureBucketExists();
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(content, contentLength));
        return key;
    }

    @Override
    public InputStream get(String key) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public URI presignedGetUrl(String key, Duration ttl) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        try {
            return s3Presigner.presignGetObject(request).url().toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Некоректне підписане посилання для " + key, e);
        }
    }
}
