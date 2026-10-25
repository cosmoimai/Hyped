package com.hyped.app.media.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface MediaObjects {
    record Upload(String url, Map<String, String> headers) {
        @Override
        public String toString() {
            return "MediaUpload[REDACTED]";
        }
    }

    Upload upload(UUID id, String type, int size);

    byte[] read(UUID id, String type, int size);

    void publish(UUID id, byte[] sanitized);

    String delivery(UUID id);

    void delete(UUID id);
}
