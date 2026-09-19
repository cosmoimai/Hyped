package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.model.AuthenticationExchangeResult;
import com.hyped.app.identity.application.model.CreateSessionCommand;
import com.hyped.app.identity.application.model.CreateSessionResult;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.domain.IdentityProvider;
import com.hyped.app.identity.domain.UserIdentity;
import com.hyped.app.profile.application.port.out.UserProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class AuthenticationExchangeTransactionService {
    private final UserAccountRepository users;
    private final UserIdentityRepository identities;
    private final UserProfileRepository profiles;
    private final CreateSessionService sessions;
    private final Clock clock;

    public AuthenticationExchangeTransactionService(
            UserAccountRepository users,
            UserIdentityRepository identities,
            UserProfileRepository profiles,
            CreateSessionService sessions,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.profiles = profiles;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public Optional<AuthenticationExchangeResult> exchangeExisting(
            IdentityProvider provider, byte[] providerSubjectHmac, CreateSessionCommand command) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerSubjectHmac, "providerSubjectHmac");
        Objects.requireNonNull(command, "command");
        Optional<UserIdentity> found = identities.findByProviderSubject(provider, providerSubjectHmac);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserIdentity identity = found.orElseThrow();
        if (!identity.userId().equals(command.userId())) {
            throw new IllegalArgumentException("Session command does not belong to the provider identity");
        }
        CreateSessionResult session = sessions.create(command);
        AuthenticationExchangeResult result = mapSessionResult(session, identity.userId(), false);
        if (session instanceof CreateSessionResult.Success) {
            identities.save(verifiedNow(identity));
        }
        return Optional.of(result);
    }

    @Transactional
    public AuthenticationExchangeResult createNew(
            PreparedNewAccount prepared, CreateSessionCommand command) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(command, "command");
        if (!prepared.account().id().equals(command.userId())) {
            throw new IllegalArgumentException("Session command does not belong to the prepared account");
        }
        UserIdentity newIdentity = prepared.identity();
        if (identities.findByProviderSubject(
                newIdentity.provider(), newIdentity.providerSubjectHmac()).isPresent()) {
            throw new ProviderIdentityRaceException();
        }
        if (newIdentity.emailHmac() != null
                && !identities.findVerifiedByEmailHmac(newIdentity.emailHmac()).isEmpty()) {
            return new AuthenticationExchangeResult.AccountLinkConfirmationRequired();
        }
        users.save(prepared.account());
        profiles.save(prepared.profile());
        if (!identities.createIfProviderSubjectAbsent(newIdentity)) {
            throw new ProviderIdentityRaceException();
        }
        CreateSessionResult session = sessions.create(command);
        if (!(session instanceof CreateSessionResult.Success success)) {
            throw new IllegalStateException("A newly persisted active account could not create its first session");
        }
        return new AuthenticationExchangeResult.Success(success.tokens(), command.userId(), true);
    }

    private UserIdentity verifiedNow(UserIdentity identity) {
        Instant verifiedAt = max(identity.lastVerifiedAt(), clock.instant());
        return new UserIdentity(identity.id(), identity.userId(), identity.provider(),
                identity.providerSubjectHmac(), identity.emailCiphertext(), identity.emailHmac(),
                identity.emailVerified(), identity.linkedAt(), verifiedAt, identity.createdAt());
    }

    private static AuthenticationExchangeResult mapSessionResult(
            CreateSessionResult result, com.hyped.app.identity.domain.UserId userId, boolean newAccount) {
        if (result instanceof CreateSessionResult.Success success) {
            return new AuthenticationExchangeResult.Success(success.tokens(), userId, newAccount);
        }
        if (result instanceof CreateSessionResult.AccountLocked locked) {
            return new AuthenticationExchangeResult.AccountUnavailable(
                    com.hyped.app.identity.domain.AccountStatus.LOCKED, locked.lockedUntil());
        }
        if (result instanceof CreateSessionResult.AccountBlocked blocked) {
            return new AuthenticationExchangeResult.AccountUnavailable(blocked.status(), null);
        }
        CreateSessionResult.DeviceLimitReached limit = (CreateSessionResult.DeviceLimitReached) result;
        return new AuthenticationExchangeResult.DeviceLimitReached(limit.devices());
    }

    private static Instant max(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
