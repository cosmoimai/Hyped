package com.hyped.app.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyped.app.media.application.MediaException;
import com.hyped.app.media.infrastructure.BoundedPhotoProcessor;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

class PhotoProcessorTest {
    byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    @Test
    void safeDecodeFlattensTransparencyAndStripsOriginalMetadata() throws Exception {
        try (var processor = new BoundedPhotoProcessor()) {
            var writer = ImageIO.getImageWritersByFormatName("png").next();
            var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            var metadata =
                    writer.getDefaultImageMetadata(
                            javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image),
                            writer.getDefaultWriteParam());
            var tree = new javax.imageio.metadata.IIOMetadataNode("javax_imageio_png_1.0");
            var text = new javax.imageio.metadata.IIOMetadataNode("tEXt");
            var entry = new javax.imageio.metadata.IIOMetadataNode("tEXtEntry");
            entry.setAttribute("keyword", "Comment");
            entry.setAttribute("value", "GPS metadata fixture");
            text.appendChild(entry);
            tree.appendChild(text);
            metadata.mergeTree("javax_imageio_png_1.0", tree);
            var buffer = new ByteArrayOutputStream();
            try (var output = ImageIO.createImageOutputStream(buffer)) {
                writer.setOutput(output);
                writer.write(
                        null,
                        new javax.imageio.IIOImage(image, null, metadata),
                        writer.getDefaultWriteParam());
            } finally {
                writer.dispose();
            }
            byte[] source = buffer.toByteArray();
            assertThat(new String(source, java.nio.charset.StandardCharsets.ISO_8859_1))
                    .contains("GPS metadata fixture");
            byte[] result = processor.sanitize(source, "image/png", 8, 8);
            assertThat(result[0] & 255).isEqualTo(255);
            assertThat(result[1] & 255).isEqualTo(216);
            assertThat(new String(result, java.nio.charset.StandardCharsets.ISO_8859_1))
                    .doesNotContain("GPS metadata fixture");
        }
    }

    @Test
    void dimensionsAndDeclaredTypeAreVerifiedBeforePublication() throws Exception {
        try (var processor = new BoundedPhotoProcessor()) {
            assertThatThrownBy(() -> processor.sanitize(png(), "image/png", 2048, 8))
                    .isInstanceOf(MediaException.class);
            assertThatThrownBy(() -> processor.sanitize(png(), "image/jpeg", 8, 8))
                    .isInstanceOf(MediaException.class);
            assertThatThrownBy(() -> processor.sanitize(new byte[1572865], "image/png", 8, 8))
                    .isInstanceOf(MediaException.class);
            assertThatThrownBy(() -> processor.sanitize(new byte[100], "image/png", 8, 8))
                    .isInstanceOf(MediaException.class);
        }
    }

    @Test
    void animationChunksAreRejectedEvenIfDecoderWouldUseFirstFrame() throws Exception {
        byte[] source = png();
        byte[] animated = new byte[source.length + 20];
        System.arraycopy(source, 0, animated, 0, 8);
        java.nio.ByteBuffer.wrap(animated, 8, 4).putInt(8);
        System.arraycopy(
                "acTL".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, animated, 12, 4);
        System.arraycopy(source, 8, animated, 28, source.length - 8);
        try (var processor = new BoundedPhotoProcessor()) {
            assertThatThrownBy(() -> processor.sanitize(animated, "image/png", 8, 8))
                    .isInstanceOf(MediaException.class);
        }
    }
}
