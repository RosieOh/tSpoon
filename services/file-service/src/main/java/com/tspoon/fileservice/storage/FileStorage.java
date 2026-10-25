package com.tspoon.fileservice.storage;

import com.tspoon.fileservice.config.FileStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * S3 호환 저장소. 바이트는 클라이언트가 Presigned URL 로 저장소와 직접 주고받고, 서비스는 URL 발급·확인·삭제만 한다.
 * 경로 방식(path-style) 주소를 써서 SeaweedFS·MinIO 같은 S3 호환 저장소에서도 그대로 동작한다.
 */
@Slf4j
@Component
public class FileStorage implements AutoCloseable {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private volatile boolean bucketReady;

    public FileStorage(FileStorageProperties properties) {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        Region region = Region.of(properties.region());
        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.publicEndpoint()))
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.bucket = properties.bucket();
    }

    /** 버킷이 없으면 만든다 (기동 시, 실패하면 첫 요청 때 다시) */
    public void createBucketIfMissing() {
        if (bucketReady) {
            return;
        }
        try {
            client.headBucket(builder -> builder.bucket(bucket));
        } catch (NoSuchBucketException e) {
            client.createBucket(builder -> builder.bucket(bucket));
            log.info("파일 버킷 {} 을(를) 만들었습니다.", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            client.createBucket(builder -> builder.bucket(bucket));
            log.info("파일 버킷 {} 을(를) 만들었습니다.", bucket);
        }
        bucketReady = true;
    }

    /**
     * 업로드 URL. 형식과 크기를 서명에 넣으므로 클라이언트는 같은 Content-Type·Content-Length 로만 올릴 수 있다.
     */
    public PresignedPutObjectRequest presignUpload(String key, String contentType, long size, Duration ttl) {
        createBucketIfMissing();
        return presigner.presignPutObject(request -> request
                .signatureDuration(ttl)
                .putObjectRequest(put -> put.bucket(bucket).key(key).contentType(contentType).contentLength(size)));
    }

    /** 내려받기 URL. 원래 파일 이름으로 저장되게 한다 */
    public URI presignDownload(String key, String fileName, Duration ttl) {
        String disposition = "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(presigner.presignGetObject(request -> request
                .signatureDuration(ttl)
                .getObjectRequest(get -> get.bucket(bucket).key(key).responseContentDisposition(disposition)))
                .url().toString());
    }

    public Optional<HeadObjectResponse> head(String key) {
        try {
            return Optional.of(client.headObject(builder -> builder.bucket(bucket).key(key)));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public void delete(String key) {
        client.deleteObject(builder -> builder.bucket(bucket).key(key));
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }
}
