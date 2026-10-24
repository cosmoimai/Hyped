package com.hyped.app.common.security.integrity;

import java.time.Instant;

public interface IntegrityReplayStore {
    boolean claim(String tokenDigest, String requestDigest, Instant now);
    boolean allow(String sessionDigest, Instant now);
}
