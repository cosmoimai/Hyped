package com.hyped.app.media.application.port.out;

public interface PhotoProcessor {
    byte[] sanitize(byte[] input, String type, int width, int height);
}
