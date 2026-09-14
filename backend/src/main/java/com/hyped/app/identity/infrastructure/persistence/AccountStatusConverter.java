package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.AccountStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AccountStatusConverter implements AttributeConverter<AccountStatus, String> {

    @Override
    public String convertToDatabaseColumn(AccountStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ACTIVE -> "active";
            case LOCKED -> "locked";
            case SUSPENDED -> "suspended";
            case COMPROMISED -> "compromised";
            case DELETION_PENDING -> "deletion_pending";
            case DELETED -> "deleted";
        };
    }

    @Override
    public AccountStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "active" -> AccountStatus.ACTIVE;
            case "locked" -> AccountStatus.LOCKED;
            case "suspended" -> AccountStatus.SUSPENDED;
            case "compromised" -> AccountStatus.COMPROMISED;
            case "deletion_pending" -> AccountStatus.DELETION_PENDING;
            case "deleted" -> AccountStatus.DELETED;
            default -> throw new IllegalArgumentException("Unknown account status: " + value);
        };
    }
}
