package com.hyped.app.media.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyped.app.media.application.port.out.PhotoModerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "hyped.media.enabled", havingValue = "true")
public class CloudMediaConfiguration {
    @Bean(destroyMethod = "close")
    R2MediaObjects mediaObjects(
            @Value("${hyped.media.r2-endpoint}") String endpoint,
            @Value("${hyped.media.r2-bucket}") String bucket,
            @Value("${hyped.media.r2-access-key}") String access,
            @Value("${hyped.media.r2-secret-key}") String secret,
            @Value("${hyped.policy.privacy-version}") String privacy,
            @Value("${hyped.policy.terms-version}") String terms) {
        if (privacy.compareTo("2026-10-25") < 0 || terms.compareTo("2026-10-25") < 0) {
            throw new IllegalStateException(
                    "Photo uploads require the updated legal documents and acceptance versions");
        }
        URI uri = URI.create(endpoint);
        if (!endpoint.matches("https://[a-f0-9]{32}\\.r2\\.cloudflarestorage\\.com")
                || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                || access.isBlank()
                || secret.isBlank()) {
            throw new IllegalStateException("Private media configuration is invalid");
        }
        var credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret));
        var configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        S3Client client =
                S3Client.builder()
                        .region(Region.of("auto"))
                        .endpointOverride(uri)
                        .credentialsProvider(credentials)
                        .serviceConfiguration(configuration)
                        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                        .overrideConfiguration(
                                c ->
                                        c.apiCallTimeout(Duration.ofSeconds(20))
                                                .apiCallAttemptTimeout(Duration.ofSeconds(15)))
                        .httpClientBuilder(
                                UrlConnectionHttpClient.builder()
                                        .connectionTimeout(Duration.ofSeconds(5))
                                        .socketTimeout(Duration.ofSeconds(15)))
                        .build();
        S3Presigner signer =
                S3Presigner.builder()
                        .region(Region.of("auto"))
                        .endpointOverride(uri)
                        .credentialsProvider(credentials)
                        .serviceConfiguration(configuration)
                        .build();
        return new R2MediaObjects(client, signer, bucket);
    }

    @Bean
    PhotoModerator photoModerator(ObjectMapper mapper) throws java.io.IOException {
        return new VisionPhotoModerator(
                com.google.auth.oauth2.GoogleCredentials.getApplicationDefault()
                        .createScoped("https://www.googleapis.com/auth/cloud-platform"),
                mapper);
    }
}
