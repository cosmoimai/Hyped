package com.hyped.app.common.security.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class GoogleIntegrityDecoderTest {
    private final HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final GoogleIntegrityDecoder decoder = new GoogleIntegrityDecoder(
            new IntegrityProperties(IntegrityProperties.Mode.OBSERVE, "com.hyped.hyped", Set.of()),
            new ObjectMapper(), http, () -> "SERVER_OAUTH_FIXTURE");

    private void returns(int status, String body) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
    }

    @Test
    void usesOnlyOfficialServerDecodeWithServerAuthorizationAndTimeout() throws Exception {
        returns(200, "{\"tokenPayloadExternal\":{\"requestDetails\":{\"requestHash\":\"bound\"}}}");
        assertThat(decoder.decode("CLIENT_EVIDENCE").path("requestDetails").path("requestHash").asText())
                .isEqualTo("bound");
        var capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(capture.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpRequest request = capture.getValue();
        assertThat(request.uri().toString())
                .isEqualTo("https://playintegrity.googleapis.com/v1/com.hyped.hyped:decodeIntegrityToken");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer SERVER_OAUTH_FIXTURE");
        assertThat(request.timeout()).contains(java.time.Duration.ofSeconds(5));
    }

    @Test
    void providerErrorsCannotExposeResponseOrCredentials() throws Exception {
        returns(400, "PRIVATE_PROVIDER_BODY CLIENT_EVIDENCE SERVER_OAUTH_FIXTURE");
        assertThatThrownBy(() -> decoder.decode("CLIENT_EVIDENCE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Integrity verification unavailable");
    }

    @Test
    void missingPayloadIsNotConvertedToPassingVerdict() throws Exception {
        returns(200, "{}");
        assertThat(decoder.decode("CLIENT_EVIDENCE").isMissingNode()).isTrue();
    }
}
