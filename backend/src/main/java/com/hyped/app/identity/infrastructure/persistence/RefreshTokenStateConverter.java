package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.RefreshTokenState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RefreshTokenStateConverter implements AttributeConverter<RefreshTokenState, String> {

    @Override
    public String convertToDatabaseColumn(RefreshTokenState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case ACTIVE -> "active";
            case CONSUMED -> "consumed";
            case REVOKED -> "revoked";
        };
    }

    @Override
    public RefreshTokenState convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "active" -> RefreshTokenState.ACTIVE;
            case "consumed" -> RefreshTokenState.CONSUMED;
            case "revoked" -> RefreshTokenState.REVOKED;
            default -> throw new IllegalArgumentException("Unknown refresh-token state: " + value);
        };
    }
}
