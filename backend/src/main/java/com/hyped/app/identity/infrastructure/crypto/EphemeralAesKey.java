package com.hyped.app.identity.infrastructure.crypto;

import java.io.Serial;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

final class EphemeralAesKey implements SecretKey, Destroyable {
    @Serial
    private static final long serialVersionUID = 1L;
    private byte[] material;

    EphemeralAesKey(byte[] material) {
        this.material = material.clone();
    }

    @Override
    public String getAlgorithm() {
        return "AES";
    }

    @Override
    public String getFormat() {
        return "RAW";
    }

    @Override
    public synchronized byte[] getEncoded() {
        return material == null ? null : material.clone();
    }

    @Override
    public synchronized void destroy() throws DestroyFailedException {
        if (material != null) {
            Arrays.fill(material, (byte) 0);
            material = null;
        }
    }

    @Override
    public synchronized boolean isDestroyed() {
        return material == null;
    }

    @Override
    public String toString() {
        return "EphemeralAesKey[REDACTED]";
    }
}
