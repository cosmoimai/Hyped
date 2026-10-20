package com.hyped.app.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.hyped.app.notification.application.port.out.PushSender;
import com.hyped.app.notification.infrastructure.FirebasePushSender;
import com.hyped.app.notification.infrastructure.PushWorkerSecurity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PushSenderTest {
    @Test
    void classifiesProviderFailuresWithoutExposingExceptionText() throws Exception {
        for (var code : MessagingErrorCode.values()) {
            var messaging = mock(FirebaseMessaging.class);
            var failure = mock(FirebaseMessagingException.class);
            when(failure.getMessagingErrorCode()).thenReturn(code);
            when(messaging.send(any())).thenThrow(failure);
            var result = new FirebasePushSender(messaging).send("private-token", Map.of("type", "EVENT_REMINDER"));
            var expected = switch (code) {
                case UNREGISTERED, SENDER_ID_MISMATCH -> PushSender.Outcome.INVALID_TOKEN;
                case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> PushSender.Outcome.RETRY;
                default -> PushSender.Outcome.FAILED;
            };
            assertThat(result).isEqualTo(expected);
        }
    }

    @Test
    void unknownProviderExceptionIsNotPropagatedOrRetried() throws Exception {
        var messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any())).thenThrow(new IllegalStateException("private-token"));
        assertThat(new FirebasePushSender(messaging).send("private-token", Map.of()))
                .isEqualTo(PushSender.Outcome.UNKNOWN);
    }

    @Test
    void schedulerRequiresExactAudienceVerifiedServiceAccount() {
        var validator = PushWorkerSecurity.identity("https://worker", "scheduler@test");
        var valid = Jwt.withTokenValue("oidc").header("alg", "RS256").subject("service-account")
                .audience(java.util.List.of("https://worker")).claim("email", "scheduler@test")
                .claim("email_verified", true).build();
        assertThat(validator.validate(valid).hasErrors()).isFalse();
        var wrong = Jwt.withTokenValue("mobile").header("alg", "RS256").subject("user")
                .audience(java.util.List.of("mobile")).claim("email", "scheduler@test")
                .claim("email_verified", true).build();
        assertThat(validator.validate(wrong).hasErrors()).isTrue();
    }
}
