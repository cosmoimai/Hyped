package com.hyped.app.identity.infrastructure.persistence;

import com.hyped.app.identity.domain.DevicePlatform;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DevicePlatformConverter implements AttributeConverter<DevicePlatform, String> {

    @Override
    public String convertToDatabaseColumn(DevicePlatform platform) {
        if (platform == null) {
            return null;
        }
        return switch (platform) {
            case ANDROID -> "android";
            case IOS -> "ios";
        };
    }

    @Override
    public DevicePlatform convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "android" -> DevicePlatform.ANDROID;
            case "ios" -> DevicePlatform.IOS;
            default -> throw new IllegalArgumentException("Unknown device platform: " + value);
        };
    }
}
