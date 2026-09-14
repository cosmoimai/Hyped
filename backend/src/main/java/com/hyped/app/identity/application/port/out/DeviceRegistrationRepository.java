package com.hyped.app.identity.application.port.out;

import com.hyped.app.identity.domain.DeviceId;
import com.hyped.app.identity.domain.DeviceRegistration;
import com.hyped.app.identity.domain.InstallationId;
import com.hyped.app.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceRegistrationRepository {

    Optional<DeviceRegistration> findByUserAndInstallation(UserId userId, InstallationId installationId);

    Optional<DeviceRegistration> findByIdAndUserId(DeviceId deviceId, UserId userId);

    List<DeviceRegistration> findActiveByUserId(UserId userId);

    // The caller must lock the account before counting when enforcing an installation limit.
    long countActiveByUserId(UserId userId);

    DeviceRegistration save(DeviceRegistration device);

    /**
     * Invalidates an active device owned by the user. Returns false if no row was updated,
     * including when the supplied timestamp precedes device creation.
     */
    boolean invalidate(DeviceId deviceId, UserId userId, Instant invalidatedAt);
}
