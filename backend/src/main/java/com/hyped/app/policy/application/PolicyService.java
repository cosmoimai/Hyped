package com.hyped.app.policy.application;

import com.hyped.app.account.application.AccountException;
import com.hyped.app.identity.application.port.out.UserAccountRepository;
import com.hyped.app.identity.domain.AccountStatus;
import com.hyped.app.identity.domain.UserId;
import com.hyped.app.policy.application.port.out.PolicyAcceptanceStore;
import com.hyped.app.policy.application.port.out.PolicyAcceptanceStore.Acceptance;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {
    private final PolicyCatalog catalog;
    private final PolicyAcceptanceStore store;
    private final UserAccountRepository accounts;
    private final Clock clock;
    public PolicyService(PolicyCatalog catalog, PolicyAcceptanceStore store, UserAccountRepository accounts,
            Clock clock) {
        this.catalog = catalog;
        this.store = store;
        this.accounts = accounts;
        this.clock = clock;
    }
    public record Status(PolicyCatalog.Versions required, PolicyCatalog.Documents documents,
            boolean documentsAvailable, Acceptance accepted, boolean currentAccepted) { }
    @Transactional(readOnly = true)
    public Status status(UserId actor) {
        var current = store.find(actor.value(), catalog.required());
        return new Status(catalog.required(), catalog.documents(), catalog.available(),
                current.or(() -> store.latest(actor.value())).orElse(null),
                current.filter(Acceptance::adultAffirmed).isPresent());
    }
    @Transactional
    public Status accept(UserId actor, PolicyCatalog.Versions versions, boolean accepted,
            boolean privacyAccepted, boolean adultAffirmed) {
        var account = accounts.findByIdForUpdate(actor).orElseThrow(() ->
                new AccountException(401, "SESSION_EXPIRED", "Sign in again."));
        if (account.status() != AccountStatus.ACTIVE && account.status() != AccountStatus.LOCKED) {
            throw new AccountException(403, "ACCOUNT_UNAVAILABLE", "This account is unavailable.");
        }
        requireDocuments();
        if (!catalog.required().equals(versions)) {
            throw new AccountException(409, "POLICY_VERSION_CHANGED", "Review the current document versions.");
        }
        if (!accepted || !privacyAccepted || !adultAffirmed) {
            throw new AccountException(422, "EXPLICIT_CONSENT_REQUIRED", "Confirm each required choice explicitly.");
        }
        store.accept(actor.value(), versions, clock.instant());
        return status(actor);
    }
    /** Call after the account lock and before any create/join mutation, including replayed joins. */
    public void requireCurrent(UserId actor) {
        requireDocuments();
        if (store.find(actor.value(), catalog.required()).filter(Acceptance::adultAffirmed).isEmpty()) {
            throw new AccountException(403, "CURRENT_POLICY_ACCEPTANCE_REQUIRED",
                    "Accept the current policies and confirm that you are at least 18 before creating or joining.");
        }
    }
    private void requireDocuments() {
        if (!catalog.available()) {
            throw new AccountException(503, "LEGAL_DOCUMENTS_UNAVAILABLE",
                    "Legal documents are temporarily unavailable.");
        }
    }
}
