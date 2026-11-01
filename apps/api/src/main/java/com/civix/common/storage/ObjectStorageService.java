package com.civix.common.storage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.civix.config.CivixProperties;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Private object storage (MinIO locally, S3 in production). Every write requests server-side encryption
 * ({@code S3_SSE}). All keys live in a single private bucket; nothing is ever served by URL.
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final S3Client s3;
    private final String bucket;
    private final String sse;

    public ObjectStorageService(S3Client s3, CivixProperties props) {
        this.s3 = s3;
        this.bucket = props.storage().bucket();
        this.sse = props.storage().sse();
    }

    public void put(String key, byte[] content, String contentType) {
        PutObjectRequest.Builder req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length);
        if (sse != null && !sse.isBlank()) {
            req.serverSideEncryption(ServerSideEncryption.fromValue(sse));
        }
        try {
            s3.putObject(req.build(), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageUnavailableException("The file could not be stored. Please try again later.", e);
        }
    }

    /** Opens an object for streaming; empty if it does not exist. The caller must close the stream. */
    public Optional<StoredObject> get(String key) {
        try {
            ResponseInputStream<GetObjectResponse> in = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            GetObjectResponse meta = in.response();
            return Optional.of(new StoredObject(in, meta.contentType(), meta.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageUnavailableException("The file could not be read. Please try again later.", e);
        } catch (SdkException e) {
            throw new StorageUnavailableException("The file could not be read. Please try again later.", e);
        }
    }

    /** Idempotent: deleting a missing key succeeds. */
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new StorageUnavailableException("The file could not be deleted. Please try again later.", e);
        }
    }

    /** Server-side copy within the bucket. Returns false if the source does not exist. */
    public boolean copy(String sourceKey, String destinationKey) {
        CopyObjectRequest.Builder req = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(destinationKey);
        if (sse != null && !sse.isBlank()) {
            req.serverSideEncryption(ServerSideEncryption.fromValue(sse));
        }
        try {
            s3.copyObject(req.build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageUnavailableException("The file could not be copied.", e);
        } catch (SdkException e) {
            throw new StorageUnavailableException("The file could not be copied.", e);
        }
    }

    /** Deletes every object under {@code prefix}; returns the number of deleted objects. */
    public int deletePrefix(String prefix) {
        int deleted = 0;
        String continuation = null;
        try {
            do {
                ListObjectsV2Request.Builder req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).maxKeys(1000);
                if (continuation != null) {
                    req.continuationToken(continuation);
                }
                ListObjectsV2Response page = s3.listObjectsV2(req.build());
                List<String> keys = new ArrayList<>();
                for (S3Object o : page.contents()) {
                    keys.add(o.key());
                }
                for (String key : keys) {
                    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
                    deleted++;
                }
                continuation = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (continuation != null);
        } catch (SdkException e) {
            throw new StorageUnavailableException("Stored files could not be deleted. Please try again later.", e);
        }
        return deleted;
    }

    /** Creates the bucket if it is missing. Never throws; returns whether the bucket is usable. */
    public boolean ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return createBucket();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return createBucket();
            }
            log.warn("Object storage bucket '{}' check failed: HTTP {}", bucket, e.statusCode());
            return false;
        } catch (SdkException e) {
            log.warn("Object storage unreachable while checking bucket '{}': {}", bucket, e.getClass().getSimpleName());
            return false;
        }
    }

    /** Lightweight reachability probe for the readiness detail. */
    public boolean isReachable() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (SdkException e) {
            return false;
        }
    }

    private boolean createBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created object storage bucket '{}'", bucket);
            return true;
        } catch (SdkException e) {
            log.warn("Could not create object storage bucket '{}': {}", bucket, e.getClass().getSimpleName());
            return false;
        }
    }

    /** An open object stream plus metadata. */
    public record StoredObject(InputStream content, String contentType, Long contentLength) {
    }
}
