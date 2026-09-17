package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.IdentityProvider;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class IdentityProviderConverter implements AttributeConverter<IdentityProvider, String> {

    @Override
    public String convertToDatabaseColumn(IdentityProvider provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case GOOGLE -> "google";
            case APPLE -> "apple";
        };
    }

    @Override
    public IdentityProvider convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "google" -> IdentityProvider.GOOGLE;
            case "apple" -> IdentityProvider.APPLE;
            default -> throw new IllegalArgumentException("Unknown identity provider: " + value);
        };
    }
}
