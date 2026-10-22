package com.hyped.app.policy.application.port.out;

import com.hyped.app.policy.application.PolicyCatalog.Versions;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PolicyAcceptanceStore {
    record Acceptance(Versions versions, boolean adultAffirmed, Instant acceptedAt) { }
    Optional<Acceptance> find(UUID user, Versions versions);
    Optional<Acceptance> latest(UUID user);
    void accept(UUID user, Versions versions, Instant now);
}
