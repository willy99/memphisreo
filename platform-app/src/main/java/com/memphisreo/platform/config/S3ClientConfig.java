package com.memphisreo.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/** MinIO зараз, AWS S3 пізніше — та сама конфігурація, інший endpoint. docs/architecture.md §7. */
@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(
            @Value("${memphisreo.storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${memphisreo.storage.access-key:memphisreo}") String accessKey,
            @Value("${memphisreo.storage.secret-key:memphisreo123}") String secretKey,
            @Value("${memphisreo.storage.region:us-east-1}") String region) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // MinIO вимагає path-style (bucket у шляху, не в subdomain).
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
