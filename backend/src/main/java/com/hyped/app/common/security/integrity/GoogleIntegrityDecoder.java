package com.hyped.app.common.security.integrity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Only Google's official server-side decode endpoint; no client-decoded verdicts are trusted. */
public final class GoogleIntegrityDecoder implements IntegrityDecoder {
    private final IntegrityProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;
    private final java.util.concurrent.Callable<String> oauthToken;
    private GoogleCredentials credentials;

    public GoogleIntegrityDecoder(IntegrityProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build(), null);
    }

    GoogleIntegrityDecoder(IntegrityProperties properties, ObjectMapper json, HttpClient http,
            java.util.concurrent.Callable<String> oauthToken) {
        this.properties = properties;
        this.json = json;
        this.http = http;
        this.oauthToken = oauthToken == null ? this::accessToken : oauthToken;
    }

    private synchronized String accessToken() throws Exception {
        if (credentials == null) {
            credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of("https://www.googleapis.com/auth/playintegrity"));
        }
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    @Override
    public JsonNode decode(String evidence) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://playintegrity.googleapis.com/v1/"
                        + properties.packageName() + ":decodeIntegrityToken"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + oauthToken.call())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("integrity_token", evidence))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().length() > 65536) {
            // Never propagate a provider response, token, URL, or provider exception.
            throw new IllegalStateException("Integrity verification unavailable");
        }
        return json.readTree(response.body()).path("tokenPayloadExternal");
    }
}
