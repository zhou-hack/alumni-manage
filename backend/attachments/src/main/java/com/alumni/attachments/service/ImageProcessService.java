package com.alumni.attachments.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图片处理（API 规范 6.1）：
 * 最长边压缩至 ≤1280px（保持原始宽高比），压缩后目标 ≤2MB（优先保尺寸后压质量），原图不留存。
 */
@Service
public class ImageProcessService {

    private static final int MAX_SIDE = 1280;
    private static final long TARGET_BYTES = 2L * 1024 * 1024;

    public record Processed(byte[] data, String ext, String mime) {
    }

    /**
     * @param raw  原始字节
     * @param mime 原始 mime（image/jpeg、image/png、image/webp）
     * @return 压缩后的字节与最终扩展名/mime（需要压质量时统一转 JPEG）
     */
    public Processed process(byte[] raw, String mime) {
        String format = formatOf(mime);
        try {
            // 先保格式缩放至 ≤1280px
            byte[] scaled = scale(raw, format, 1.0);
            if (scaled.length <= TARGET_BYTES) {
                return new Processed(scaled, format, mimeOf(format));
            }
            // 尺寸已达上限仍超 2MB：转 JPEG 逐级压质量（先保尺寸后压质量）
            double quality = 0.85;
            while (quality >= 0.4) {
                byte[] compressed = toJpeg(scaled, quality);
                if (compressed.length <= TARGET_BYTES) {
                    return new Processed(compressed, "jpg", "image/jpeg");
                }
                quality -= 0.15;
            }
            return new Processed(toJpeg(scaled, 0.4), "jpg", "image/jpeg");
        } catch (IOException | RuntimeException e) {
            // 解码失败/格式不支持统一上抛，由调用方映射 FILE_TYPE_NOT_ALLOWED
            throw new IllegalStateException("图片处理失败", e);
        }
    }

    /** 按最长边 ≤1280 缩放（原图已小于 1280 则按 1:1 过一遍格式） */
    private byte[] scale(byte[] raw, String format, double ratio) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(raw));
        if (image == null) {
            throw new IOException("无法解析图片内容");
        }
        int w = image.getWidth(), h = image.getHeight();
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h)) * ratio;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(image)
                .scale(scale)
                .outputFormat(format)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private byte[] toJpeg(byte[] raw, double quality) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(raw));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(image)
                .scale(1.0)
                .outputFormat("jpg")
                .outputQuality(quality)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private String formatOf(String mime) {
        // Thumbnailator 不支持 webp 输出：webp 输入统一归一为 png（无损），超 2MB 再走 JPEG 压质量
        return switch (mime) {
            case "image/png", "image/webp" -> "png";
            default -> "jpg";
        };
    }

    private String mimeOf(String format) {
        // format 只会是 png/jpg（webp 输入已在 formatOf 归一为 png）
        return "png".equals(format) ? "image/png" : "image/jpeg";
    }
}
