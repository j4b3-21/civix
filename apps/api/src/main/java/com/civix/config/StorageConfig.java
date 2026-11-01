package com.civix.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(CivixProperties props) {
        CivixProperties.Storage cfg = props.storage();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(cfg.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(cfg.forcePathStyle())
                        .build())
                // Only send/validate checksums when S3 requires them: keeps S3-compatible stores (MinIO) happy.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10))
                        .build());
        if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(cfg.endpoint()));
        }
        return builder.build();
    }
}
