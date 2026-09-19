package com.hyped.app.identity.infrastructure.crypto;

import com.hyped.app.identity.application.exception.PersonalDataProtectionException;
import com.hyped.app.identity.application.exception.PersonalDataProtectionException.Reason;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hyped.identity-crypto", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdentityCryptoProperties.class)
public class IdentityCryptoConfiguration {
    @Bean
    IdentityLookupProtector identityLookupProtector(IdentityCryptoProperties properties) {
        byte[] bytes = null;
        try (var input = properties.lookupHmacKey().getInputStream()) {
            // Resource format is raw binary, with a bounded read to reject misconfigured resources.
            bytes = input.readNBytes(4097);
            if (bytes.length > 4096) {
                throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
            }
            return new HmacSha256IdentityLookupProtector(bytes);
        } catch (IOException exception) {
            throw new PersonalDataProtectionException(Reason.KEY_UNAVAILABLE);
        } finally {
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }
}
