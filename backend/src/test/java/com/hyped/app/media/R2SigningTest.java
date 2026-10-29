package com.hyped.app.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyped.app.media.infrastructure.R2MediaObjects;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.UUID;

class R2SigningTest {
    @Test
    void uploadAndDeliveryUseSeparatePrivateKeysAndBoundedExpiry() {
        var signer =
                S3Presigner.builder()
                        .region(Region.of("auto"))
                        .endpointOverride(
                                URI.create(
                                        "https://" + "a".repeat(32) + ".r2.cloudflarestorage.com"))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                "fixture-access", "fixture-secret")))
                        .serviceConfiguration(
                                S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .build();
        try (var cloud =
                new R2MediaObjects(
                        org.mockito.Mockito.mock(S3Client.class), signer, "private-photos")) {
            UUID id = UUID.randomUUID();
            var upload = cloud.upload(id, "image/jpeg", 800);
            assertThat(URI.create(upload.url()).getPath()).contains("/quarantine/");
            assertThat(URI.create(upload.url()).getRawQuery()).contains("X-Amz-Expires=600", "content-length");
            assertThat(upload.headers().keySet())
                    .allSatisfy(
                            key ->
                                    assertThat(key.toLowerCase())
                                            .isIn(
                                                    "content-type",
                                                    "content-length",
                                                    "x-amz-checksum-sha256"));
            assertThat(URI.create(cloud.delivery(id)).getPath()).contains("/ready/");
            assertThat(URI.create(cloud.delivery(id)).getRawQuery()).contains("X-Amz-Expires=900");
            assertThat(upload.toString()).doesNotContain("X-Amz");
        }
    }
}
