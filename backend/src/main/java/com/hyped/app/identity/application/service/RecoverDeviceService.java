package com.hyped.app.identity.application.service;

import com.hyped.app.identity.application.model.VerifiedProviderIdentity;
import com.hyped.app.identity.application.port.out.IdentityLookupProtector;
import com.hyped.app.identity.application.port.out.IdentityTokenVerifier;
import com.hyped.app.identity.application.port.out.UserIdentityRepository;
import com.hyped.app.identity.domain.DeviceId;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "hyped.tokens", name = "enabled", havingValue = "true")
public class RecoverDeviceService {
    private final IdentityTokenVerifier verifier;
    private final IdentityLookupProtector lookupProtector;
    private final UserIdentityRepository identities;
    private final RecoverDeviceTransactionService transactions;

    public RecoverDeviceService(
            IdentityTokenVerifier verifier,
            IdentityLookupProtector lookupProtector,
            UserIdentityRepository identities,
            RecoverDeviceTransactionService transactions) {
        this.verifier = verifier;
        this.lookupProtector = lookupProtector;
        this.identities = identities;
        this.transactions = transactions;
    }

    public boolean recover(String firebaseIdToken, DeviceId deviceId) {
        Objects.requireNonNull(firebaseIdToken, "firebaseIdToken");
        Objects.requireNonNull(deviceId, "deviceId");
        VerifiedProviderIdentity verified = verifier.verify(firebaseIdToken);
        byte[] subjectHmac = lookupProtector.protectProviderSubject(
                verified.provider(), verified.providerSubject());
        return identities.findByProviderSubject(verified.provider(), subjectHmac)
                .map(identity -> transactions.revokeDevice(identity.userId(), deviceId))
                .orElse(false);
    }
}
