package com.memphisreo.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;

/**
 * Бакет перевіряється/створюється ледаче, при першому записі: старт
 * застосунку не залежить від доступності сховища.
 */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final String bucket;
    private volatile boolean bucketVerified;

    public S3ObjectStorage(S3Client s3Client,
                            @Value("${memphisreo.storage.documents-bucket:memphisreo-documents}") String bucket) {
        this.s3Client = s3Client;
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
}
