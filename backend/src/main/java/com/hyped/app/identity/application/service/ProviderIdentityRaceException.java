package com.hyped.app.identity.application.service;

final class ProviderIdentityRaceException extends RuntimeException {
    ProviderIdentityRaceException() {
        super("Provider identity was concurrently created");
    }
}
