package com.hyped.app.identity.infrastructure.config;

import com.hyped.app.identity.application.port.out.AccessTokenIssuer;
import com.hyped.app.identity.application.port.out.AuthSessionRepository;
import com.hyped.app.identity.application.port.out.DeviceRegistrationRepository;
import com.hyped.app.identity.application.port.out.IdGenerator;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import com.hyped.app.identity.application.port.out.PersonalDataCipher;
import com.hyped.app.identity.application.port.out.PersonalDataKeyManager;
import com.hyped.app.identity.application.port.out.RefreshTokenDigester;
import com.hyped.app.identity.application.port.out.RefreshTokenGenerator;
import com.hyped.app.identity.application.port.out.RefreshTokenRecordRepository;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.application.service.AuthenticationExchangeService;
import com.hyped.app.identity.application.service.AuthenticationExchangeTransactionService;
import com.hyped.app.identity.application.service.CreateSessionService;
import com.hyped.app.identity.application.service.RefreshSessionService;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class SessionServiceConfiguration {

    @Bean
    CreateSessionService createSessionService(UserAccountRepository users, DeviceRegistrationRepository devices,
            AuthSessionRepository sessions, RefreshTokenRecordRepository tokens, RefreshTokenGenerator generator,
            RefreshTokenDigester digester, AccessTokenIssuer issuer, IdGenerator ids, Clock clock) {
        return new CreateSessionService(users, devices, sessions, tokens, generator, digester, issuer, ids, clock);
    }

    @Bean
    RefreshSessionService refreshSessionService(RefreshTokenDigester digester, RefreshTokenRecordRepository tokens,
            AuthSessionRepository sessions, UserAccountRepository users, DeviceRegistrationRepository devices,
            RefreshTokenGenerator generator, AccessTokenIssuer issuer, IdGenerator ids, Clock clock) {
        return new RefreshSessionService(digester, tokens, sessions, users, devices, generator, issuer, ids, clock);
    }

    @Bean
    AuthenticationExchangeTransactionService authenticationExchangeTransactionService(
            UserAccountRepository users,
            UserIdentityRepository identities,
            UserProfileRepository profiles,
            CreateSessionService sessions,
            Clock clock) {
        return new AuthenticationExchangeTransactionService(users, identities, profiles, sessions, clock);
    }

    @Bean
    AuthenticationExchangeService authenticationExchangeService(
            IdentityTokenVerifier verifier,
            IdentityLookupProtector lookupProtector,
            UserIdentityRepository identities,
            PersonalDataKeyManager keyManager,
            PersonalDataCipher cipher,
            AuthenticationExchangeTransactionService transactions,
            IdGenerator ids,
            Clock clock) {
        return new AuthenticationExchangeService(
                verifier, lookupProtector, identities, keyManager, cipher, transactions, ids, clock);
    }
}
