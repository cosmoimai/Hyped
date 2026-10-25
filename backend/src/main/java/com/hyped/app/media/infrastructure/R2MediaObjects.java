package com.hyped.app.media.infrastructure;

import com.hyped.app.media.application.MediaException;
import com.hyped.app.media.application.port.out.MediaObjects;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.UUID;

public final class R2MediaObjects implements MediaObjects, AutoCloseable {
    private final S3Client client;
    private final S3Presigner signer;
    private final String bucket;

    public R2MediaObjects(S3Client client, S3Presigner signer, String bucket) {
        this.client = client;
        this.signer = signer;
        this.bucket = bucket;
    }

    private static String quarantine(UUID id) {
        return "quarantine/" + id;
    }

    private static String ready(UUID id) {
        return "ready/" + id + ".jpg";
    }

    @Override
    public Upload upload(UUID id, String type, int size) {
        var signed =
                signer.presignPutObject(
                        p ->
                                p.signatureDuration(Duration.ofMinutes(10))
                                        .putObjectRequest(
                                                r ->
                                                        r.bucket(bucket)
                                                                .key(quarantine(id))
                                                                .contentType(type)
                                                                .contentLength((long) size)));
        var headers = new LinkedHashMap<String, String>();
        signed.signedHeaders()
                .forEach(
                        (name, values) -> {
                            if (!name.equalsIgnoreCase("host")) {
                                headers.put(name, String.join(",", values));
                            }
                        });
        headers.put("Content-Type", type);
        headers.put("Content-Length", Integer.toString(size));
        return new Upload(signed.url().toString(), headers);
    }

    @Override
    public byte[] read(UUID id, String type, int size) {
        var head = client.headObject(r -> r.bucket(bucket).key(quarantine(id)));
        if (head.contentLength() != size || !type.equals(head.contentType())) {
            throw new MediaException(422, "MEDIA_TOO_LARGE");
        }
        try (var input = client.getObject(r -> r.bucket(bucket).key(quarantine(id)))) {
            byte[] data = input.readNBytes(1572865);
            if (data.length != size) {
                throw new MediaException(422, "MEDIA_TOO_LARGE");
            }
            return data;
        } catch (java.io.IOException failure) {
            throw MediaException.unavailable();
        }
    }

    @Override
    public byte[] readReady(UUID id) {
        try (var input=client.getObject(r -> r.bucket(bucket).key(ready(id)))) {
            byte[] bytes=input.readNBytes(1572865);
            if (bytes.length<1 || bytes.length>1572864 || !"image/jpeg".equals(input.response().contentType())) {
                throw new MediaException(422,"MEDIA_TOO_LARGE");
            }
            return bytes;
        } catch (java.io.IOException failure) { throw MediaException.unavailable(); }
    }

    @Override
    public void publish(UUID id, byte[] sanitized) {
        client.putObject(
                r ->
                        r.bucket(bucket)
                                .key(ready(id))
                                .contentType("image/jpeg")
                                .cacheControl("private, no-store"),
                RequestBody.fromBytes(sanitized));
    }

    @Override
    public String delivery(UUID id) {
        return signer.presignGetObject(
                        p ->
                                p.signatureDuration(Duration.ofMinutes(15))
                                        .getObjectRequest(
                                                r ->
                                                        r.bucket(bucket)
                                                                .key(ready(id))
                                                                .responseCacheControl(
                                                                        "private, no-store")))
                .url()
                .toString();
    }

    @Override
    public void delete(UUID id) {
        client.deleteObject(r -> r.bucket(bucket).key(quarantine(id)));
        client.deleteObject(r -> r.bucket(bucket).key(ready(id)));
    }

    @Override
    public void close() {
        signer.close();
        client.close();
    }
}
