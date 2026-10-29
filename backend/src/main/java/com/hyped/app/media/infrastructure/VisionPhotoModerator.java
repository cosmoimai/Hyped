package com.hyped.app.media.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.hyped.app.media.application.MediaException;
import com.hyped.app.media.application.port.out.PhotoModerator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class VisionPhotoModerator implements PhotoModerator {
    private final GoogleCredentials credentials;
    private final ObjectMapper mapper;
    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public VisionPhotoModerator(GoogleCredentials credentials, ObjectMapper mapper) {
        this.credentials = credentials;
        this.mapper = mapper;
    }

    @Override
    public Decision evaluate(byte[] sanitized) {
        try {
            credentials.refreshIfExpired();
            String body =
                    mapper.writeValueAsString(
                            Map.of(
                                    "requests",
                                    List.of(
                                            Map.of(
                                                    "image",
                                                            Map.of(
                                                                    "content",
                                                                    Base64.getEncoder()
                                                                            .encodeToString(
                                                                                    sanitized)),
                                                    "features",
                                                            List.of(
                                                                    Map.of(
                                                                            "type",
                                                                            "SAFE_SEARCH_DETECTION"))))));
            var request =
                    HttpRequest.newBuilder(
                                    URI.create("https://vision.googleapis.com/v1/images:annotate"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .header(
                                    "Authorization",
                                    "Bearer " + credentials.getAccessToken().getTokenValue())
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                byte[] data = input.readNBytes(131073);
                if (response.statusCode() != 200 || data.length > 131072) {
                    throw MediaException.unavailable();
                }
                var root = mapper.readTree(data).path("responses").path(0);
                if (root.has("error")) {
                    throw MediaException.unavailable();
                }
                var annotation = root.path("safeSearchAnnotation");
                boolean uncertain = false;
                for (String category : List.of("adult", "racy", "violence")) {
                    String result = annotation.path(category).asText();
                    if (List.of("LIKELY", "VERY_LIKELY").contains(result)) {
                        return Decision.REJECTED;
                    }
                    if (!List.of("VERY_UNLIKELY", "UNLIKELY").contains(result)) {
                        uncertain = true;
                    }
                }
                return uncertain ? Decision.UNCERTAIN : Decision.APPROVED;
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw MediaException.unavailable();
        }
    }
}
