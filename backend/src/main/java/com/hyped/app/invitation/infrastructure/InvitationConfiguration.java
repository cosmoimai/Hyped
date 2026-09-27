package com.hyped.app.invitation.infrastructure;

import com.hyped.app.invitation.application.port.out.InvitationCryptography;
import java.security.SecureRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvitationProperties.class)
public class InvitationConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "hyped.invitation", name = "enabled", havingValue = "true")
    InvitationCryptography invitationCryptography(InvitationProperties properties) {
        return new InvitationCipher(properties, new SecureRandom());
    }
}
