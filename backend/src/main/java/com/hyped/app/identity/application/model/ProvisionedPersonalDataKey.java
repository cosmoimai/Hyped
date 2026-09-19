package com.hyped.app.identity.application.model;

import java.util.Objects;

public record ProvisionedPersonalDataKey(String keyReference, boolean createdByRequest) {
    public ProvisionedPersonalDataKey {
        Objects.requireNonNull(keyReference, "keyReference");
        if (keyReference.isBlank() || keyReference.length() > 255) {
            throw new IllegalArgumentException("Key reference must contain between 1 and 255 characters");
        }
    }

    @Override
    public String toString() {
        return "ProvisionedPersonalDataKey[createdByRequest=" + createdByRequest + ", keyReference=[REDACTED]]";
    }
}
