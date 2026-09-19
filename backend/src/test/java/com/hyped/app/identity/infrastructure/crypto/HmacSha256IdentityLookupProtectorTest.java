package com.hyped.app.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.domain.IdentityProvider;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HmacSha256IdentityLookupProtectorTest {
    private final HmacSha256IdentityLookupProtector protector = new HmacSha256IdentityLookupProtector(key());

    static byte[] key() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        return key;
    }

    @Test
    void matchesIndependentKnownAnswerWithDeterministic32ByteOutput() {
        // Python hmac/hashlib: key bytes 0..31; big-endian length-prefixed UTF-8 domain, google, Subject.
        byte[] actual = protector.protectProviderSubject(IdentityProvider.GOOGLE, "Subject");
        assertThat(actual).hasSize(32).isEqualTo(HexFormat.of().parseHex(
                "21db547ae5493aa478d232d1dca4d6e24a6aac662c8055eba49f24668bbed9eb"));
        assertThat(protector.protectProviderSubject(IdentityProvider.GOOGLE, " Subject ")).isEqualTo(actual);
    }

    @Test
    void separatesProvidersDomainsAndSubjectCase() {
        byte[] google = protector.protectProviderSubject(IdentityProvider.GOOGLE, "subject");
        assertThat(protector.protectProviderSubject(IdentityProvider.APPLE, "subject")).isNotEqualTo(google);
        assertThat(protector.protectProviderSubject(IdentityProvider.GOOGLE, "Subject")).isNotEqualTo(google);
        assertThat(protector.protectVerifiedEmail("subject")).isNotEqualTo(google);
    }

    @Test
    void normalizesEmailCaseWhitespaceAndUnicode() {
        assertThat(protector.protectVerifiedEmail("  PERSON@EXAMPLE.COM  "))
                .isEqualTo(protector.protectVerifiedEmail("person@example.com"));
        assertThat(protector.protectVerifiedEmail(" E\u0301@EXAMPLE.COM "))
                .isEqualTo(protector.protectVerifiedEmail("é@example.com"));
        assertThat(protector.protectVerifiedEmail("I@example.com"))
                .isEqualTo(protector.protectVerifiedEmail("i@example.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingValues(String value) {
        assertThatThrownBy(() -> protector.protectProviderSubject(IdentityProvider.GOOGLE, value))
                .isInstanceOf(PersonalDataProtectionException.class).hasMessage("INVALID_INPUT");
        assertThatThrownBy(() -> protector.protectVerifiedEmail(value))
                .isInstanceOf(PersonalDataProtectionException.class).hasMessage("INVALID_INPUT");
    }

    @Test
    void rejectsOversizedInputsNullProviderAndShortKeys() {
        assertThatThrownBy(() -> protector.protectProviderSubject(IdentityProvider.APPLE, "s".repeat(256)))
                .hasMessage("INVALID_INPUT");
        assertThatThrownBy(() -> protector.protectVerifiedEmail("e".repeat(321))).hasMessage("INVALID_INPUT");
        assertThatThrownBy(() -> protector.protectProviderSubject(null, "subject")).hasMessage("INVALID_INPUT");
        assertThatThrownBy(() -> new HmacSha256IdentityLookupProtector(new byte[31])).hasMessage("KEY_UNAVAILABLE");
        assertThatThrownBy(() -> new HmacSha256IdentityLookupProtector(null)).hasMessage("KEY_UNAVAILABLE");
        assertThat(protector.protectVerifiedEmail("e".repeat(320))).hasSize(32);
        assertThat(protector.protectProviderSubject(IdentityProvider.APPLE, "s".repeat(255))).hasSize(32);
    }

    @Test
    void copiesKeyAndReturnsIndependentArrays() {
        byte[] inputKey = key();
        var local = new HmacSha256IdentityLookupProtector(inputKey);
        byte[] expected = local.protectVerifiedEmail("person@example.com");
        Arrays.fill(inputKey, (byte) 99);
        byte[] result = local.protectVerifiedEmail("person@example.com");
        assertThat(result).isEqualTo(expected).isNotSameAs(expected);
        Arrays.fill(result, (byte) 0);
        assertThat(local.protectVerifiedEmail("person@example.com")).isEqualTo(expected);
    }

    @Test
    void concurrentOperationsUseIndependentMacState() throws Exception {
        byte[] expected = protector.protectVerifiedEmail("person@example.com");
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<byte[]>> tasks = IntStream.range(0, 200)
                    .mapToObj(i -> (Callable<byte[]>) () -> protector.protectVerifiedEmail("person@example.com"))
                    .toList();
            for (var result : executor.invokeAll(tasks)) {
                assertThat(result.get()).isEqualTo(expected).isNotSameAs(expected);
            }
        }
    }
}
