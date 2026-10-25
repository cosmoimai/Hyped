package com.hyped.app.media.infrastructure;

import com.hyped.app.media.application.MediaException;
import com.hyped.app.media.application.port.out.PhotoProcessor;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

@Component
public final class BoundedPhotoProcessor implements PhotoProcessor, AutoCloseable {
    private final ThreadPoolExecutor workers =
            new ThreadPoolExecutor(
                    2,
                    2,
                    0,
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    runnable -> {
                        Thread thread = new Thread(runnable, "bounded-photo-validation");
                        thread.setDaemon(true);
                        return thread;
                    });

    @Override
    public byte[] sanitize(byte[] input, String type, int width, int height) {
        java.util.concurrent.Future<byte[]> work = null;
        try {
            work = workers.submit(() -> decode(input, type, width, height));
            return work.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            if (work != null) {
                work.cancel(true);
            }
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (failure.getCause() instanceof MediaException media) {
                throw media;
            }
            throw new MediaException(422, "MEDIA_DECODE_FAILED");
        }
    }

    private static byte[] decode(byte[] input, String type, int width, int height)
            throws java.io.IOException {
        if (input.length < 12 || input.length > 1572864) {
            throw new MediaException(422, "MEDIA_TOO_LARGE");
        }
        boolean jpeg = (input[0] & 255) == 255 && (input[1] & 255) == 216;
        boolean png = input[0] == (byte) 137 && input[1] == 80 && input[2] == 78 && input[3] == 71;
        boolean webp =
                new String(input, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(input, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                                .equals("WEBP");
        if (!(jpeg && type.equals("image/jpeg")
                || png && type.equals("image/png")
                || webp && type.equals("image/webp"))) {
            throw new MediaException(415, "MEDIA_TYPE_UNSUPPORTED");
        }
        // APNG/WebP animation chunks are rejected even when a decoder exposes only the first frame.
        for (int i = png ? 8 : webp ? 12 : input.length; i + 12 <= input.length; ) {
            int length;
            String chunk;
            if (png) {
                length = java.nio.ByteBuffer.wrap(input, i, 4).getInt();
                chunk = new String(input, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            } else {
                length =
                        java.nio.ByteBuffer.wrap(input, i + 4, 4)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                .getInt();
                chunk = new String(input, i, 4, java.nio.charset.StandardCharsets.US_ASCII);
            }
            if (chunk.equals("acTL") || chunk.equals("ANIM") || chunk.equals("ANMF")) {
                throw new MediaException(415, "MEDIA_ANIMATED_UPLOAD_UNSUPPORTED");
            }
            long advance = (long) length + (png ? 12 : 8 + (length & 1));
            if (length < 0 || advance < 8 || advance > input.length - i) {
                throw new MediaException(422, "MEDIA_DECODE_FAILED");
            }
            i += (int) advance;
        }
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new MediaException(422, "MEDIA_DECODE_FAILED");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int actualWidth = reader.getWidth(0);
                int actualHeight = reader.getHeight(0);
                if (actualWidth != width
                        || actualHeight != height
                        || actualWidth > 2048
                        || actualHeight > 2048) {
                    throw new MediaException(422, "MEDIA_DIMENSIONS_EXCEEDED");
                }
                if (reader.getNumImages(true) != 1) {
                    throw new MediaException(415, "MEDIA_ANIMATED_UPLOAD_UNSUPPORTED");
                }
                BufferedImage source = reader.read(0);
                BufferedImage rgb =
                        new BufferedImage(actualWidth, actualHeight, BufferedImage.TYPE_INT_RGB);
                var graphics = rgb.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, actualWidth, actualHeight);
                    graphics.drawImage(source, 0, 0, null);
                } finally {
                    graphics.dispose();
                    source.flush();
                }
                try {
                    for (float quality : new float[] {0.85f, 0.65f, 0.45f}) {
                        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                        var bytes = new ByteArrayOutputStream();
                        try (var output = ImageIO.createImageOutputStream(bytes)) {
                            writer.setOutput(output);
                            var parameters = writer.getDefaultWriteParam();
                            parameters.setCompressionMode(
                                    javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                            parameters.setCompressionQuality(quality);
                            writer.write(
                                    null, new javax.imageio.IIOImage(rgb, null, null), parameters);
                        } finally {
                            writer.dispose();
                        }
                        if (bytes.size() <= 1572864) {
                            return bytes.toByteArray();
                        }
                    }
                    throw new MediaException(422, "MEDIA_TOO_LARGE");
                } finally {
                    rgb.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
