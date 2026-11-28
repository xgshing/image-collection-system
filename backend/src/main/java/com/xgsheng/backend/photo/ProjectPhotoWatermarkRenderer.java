package com.xgsheng.backend.photo;

import com.xgsheng.backend.entity.ProjectPhoto;
import com.xgsheng.backend.exception.BusinessException;
import com.xgsheng.backend.project.ProjectPhotoRules;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 按建议参数在内存里画水印。只使用 classpath 里的 Noto Sans SC，不用系统字体代替。
 * 展示用的「未获取到地点」「未获取到天气」不写回数据库。
 */
public final class ProjectPhotoWatermarkRenderer {

    static final String NO_LOCATION = "未获取到地点";
    static final String NO_WEATHER = "未获取到天气";
    static final String EMPTY_NOTE = "（空记录）";
    private static final String DEFAULT_PROJECT_NAME = "未命名项目";
    private static final String REGULAR_FONT = "/fonts/NotoSansSC-Regular.otf";
    private static final String BOLD_FONT = "/fonts/NotoSansSC-Bold.otf";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final Color BODY_BG = new Color(176, 178, 184, Math.round(255 * 0.48f));
    private static final Color HEADER_BG = new Color(120, 122, 128, Math.round(255 * 0.90f));
    private static final Color DOT_COLOR = new Color(0xF1, 0x9D, 0x2F);

    private ProjectPhotoWatermarkRenderer() {
    }

    public static byte[] render(byte[] original, ProjectPhoto photo) {
        if (original == null || original.length == 0 || photo == null) {
            throw BusinessException.badRequest(ProjectPhotoRules.RENDER_FAILED);
        }
        if (photo.getWatermarkVisible() == null || photo.getWatermarkVisible() != 1) {
            return original.clone();
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                throw BusinessException.badRequest(ProjectPhotoRules.RENDER_FAILED);
            }
            BufferedImage upright = orient(source, jpegOrientation(original));
            Font regular = loadFont(REGULAR_FONT, Font.PLAIN);
            Font bold = loadFont(BOLD_FONT, Font.BOLD);
            return draw(upright, photo, regular, bold);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(ProjectPhotoRules.RENDER_FAILED);
        }
    }

    static List<String> displayLines(ProjectPhoto photo) {
        List<String> lines = new ArrayList<>();
        lines.add(projectName(photo));
        lines.add("备注内容：" + noteValue(photo));
        lines.add("拍摄时间：" + clock(photo));
        lines.add("地　　点：" + locationValue(photo));
        lines.add("天　　气：" + weatherLine(photo));
        return lines;
    }

    private static String projectName(ProjectPhoto photo) {
        String name = photo.getProjectNameSnapshot();
        return name == null || name.isBlank() ? DEFAULT_PROJECT_NAME : name;
    }

    private static String noteValue(ProjectPhoto photo) {
        String note = photo.getNote();
        return note == null || note.isBlank() ? EMPTY_NOTE : note;
    }

    private static String locationValue(ProjectPhoto photo) {
        String location = photo.getLocationText();
        return location == null || location.isBlank() ? NO_LOCATION : location;
    }

    private static String weatherLine(ProjectPhoto photo) {
        if (!"ok".equals(photo.getWeatherStatus())
                || photo.getWeatherText() == null
                || photo.getWeatherText().isBlank()) {
            return NO_WEATHER;
        }
        if (photo.getTemperatureC() == null) {
            return photo.getWeatherText();
        }
        return photo.getWeatherText() + " " + photo.getTemperatureC().stripTrailingZeros().toPlainString() + "℃";
    }

    private static String clock(ProjectPhoto photo) {
        if (photo.getTakenAt() == null) {
            return "";
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(photo.getTimezone() == null ? "Asia/Shanghai" : photo.getTimezone());
        } catch (RuntimeException ex) {
            zone = ZoneId.of("Asia/Shanghai");
        }
        return CLOCK.format(photo.getTakenAt().atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(zone));
    }

    private static byte[] draw(BufferedImage image, ProjectPhoto photo, Font regular, Font bold) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int shortSide = Math.min(width, height);
        int margin = Math.max(4, Math.round(shortSide * 0.032f));
        int blockWidth = Math.max(120, Math.min(width - margin * 2, Math.round(shortSide * 0.50f)));
        int paddingX = Math.max(6, Math.round(blockWidth * 0.039f));
        int radius = Math.max(4, Math.round(blockWidth * 0.039f));
        int titleSize = Math.max(10, Math.round(blockWidth * 0.061f));
        int bodySize = Math.max(9, Math.round(blockWidth * 0.0556f));
        int dotSize = Math.max(5, Math.round(blockWidth * 0.033f));
        int dotGap = Math.max(4, Math.round(blockWidth * 0.022f));
        Font titleFont = bold.deriveFont((float) titleSize);
        Font bodyFont = regular.deriveFont((float) bodySize);

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, null);

            FontMetrics titleMetrics = graphics.getFontMetrics(titleFont);
            FontMetrics bodyMetrics = graphics.getFontMetrics(bodyFont);
            int titleBarHeight = Math.max(titleMetrics.getHeight() + Math.round(titleSize * 0.36f), Math.round(blockWidth * 0.106f));
            int bodyLineHeight = Math.max(bodyMetrics.getHeight(), Math.round(bodySize * 1.40f));
            int bodyPaddingY = Math.max(3, Math.round(bodySize * 0.28f));

            int labelWidth = bodyMetrics.stringWidth("备注内容");
            int colonWidth = bodyMetrics.stringWidth("：");
            int valueOffset = paddingX + labelWidth + colonWidth;
            int valueMaxWidth = Math.max(bodySize * 2, blockWidth - valueOffset - paddingX);

            List<FieldRow> rows = List.of(
                    new FieldRow("备注内容", wrapWithLimit(noteValue(photo), bodyMetrics, valueMaxWidth, 3)),
                    new FieldRow("拍摄时间", wrapWithLimit(clock(photo), bodyMetrics, valueMaxWidth, 1)),
                    new FieldRow("地点", wrapWithLimit(locationValue(photo), bodyMetrics, valueMaxWidth, 3)),
                    new FieldRow("天气", wrapWithLimit(weatherLine(photo), bodyMetrics, valueMaxWidth, 3))
            );

            int totalLines = 0;
            for (FieldRow row : rows) {
                totalLines += Math.max(1, row.valueLines.size());
            }
            int blockHeight = titleBarHeight + bodyPaddingY * 2 + totalLines * bodyLineHeight;

            int x = "top_right".equals(photo.getWatermarkPosition()) || "bottom_right".equals(photo.getWatermarkPosition())
                    ? width - margin - blockWidth
                    : margin;
            int y = "bottom_left".equals(photo.getWatermarkPosition()) || "bottom_right".equals(photo.getWatermarkPosition())
                    ? height - margin - blockHeight
                    : margin;
            x = Math.max(0, Math.min(x, width - blockWidth));
            y = Math.max(0, Math.min(y, height - blockHeight));

            graphics.setComposite(AlphaComposite.SrcOver);
            Shape box = new RoundRectangle2D.Float(x, y, blockWidth, blockHeight, radius * 2f, radius * 2f);
            Shape previousClip = graphics.getClip();
            graphics.setClip(box);

            graphics.setColor(BODY_BG);
            graphics.fillRect(x, y, blockWidth, blockHeight);

            graphics.setColor(HEADER_BG);
            graphics.fillRect(x, y, blockWidth, titleBarHeight);

            float dotX = x + paddingX;
            float dotY = y + (titleBarHeight - dotSize) / 2f;
            graphics.setColor(DOT_COLOR);
            graphics.fill(new Ellipse2D.Float(dotX, dotY, dotSize, dotSize));

            int titleX = x + paddingX + dotSize + dotGap;
            int titleMaxWidth = Math.max(titleSize, blockWidth - (titleX - x) - paddingX);
            String titleText = fitSingleLine(projectName(photo), titleMetrics, titleMaxWidth);
            int titleBaseline = y + (titleBarHeight - titleMetrics.getHeight()) / 2 + titleMetrics.getAscent();
            graphics.setFont(titleFont);
            graphics.setColor(Color.WHITE);
            graphics.drawString(titleText, titleX, titleBaseline);

            graphics.setFont(bodyFont);
            int cursorY = y + titleBarHeight + bodyPaddingY;
            for (FieldRow row : rows) {
                int firstBaseline = cursorY + (bodyLineHeight - bodyMetrics.getHeight()) / 2 + bodyMetrics.getAscent();
                drawJustifiedLabel(graphics, row.label, x + paddingX, firstBaseline, labelWidth, bodyMetrics);
                graphics.drawString("：", x + paddingX + labelWidth, firstBaseline);
                if (row.valueLines.isEmpty()) {
                    cursorY += bodyLineHeight;
                } else {
                    for (String line : row.valueLines) {
                        int baseline = cursorY + (bodyLineHeight - bodyMetrics.getHeight()) / 2 + bodyMetrics.getAscent();
                        graphics.drawString(line, x + valueOffset, baseline);
                        cursorY += bodyLineHeight;
                    }
                }
            }

            graphics.setClip(previousClip);
        } finally {
            graphics.dispose();
        }
        return writeJpeg(canvas);
    }

    private static void drawJustifiedLabel(Graphics2D graphics,
                                           String label,
                                           int x,
                                           int baseline,
                                           int labelWidth,
                                           FontMetrics metrics) {
        if (label.length() == 2) {
            String first = label.substring(0, 1);
            String second = label.substring(1, 2);
            graphics.drawString(first, x, baseline);
            graphics.drawString(second, x + labelWidth - metrics.stringWidth(second), baseline);
            return;
        }
        graphics.drawString(label, x, baseline);
    }

    private static String fitSingleLine(String text, FontMetrics metrics, int maxWidth) {
        String value = text == null ? "" : text;
        if (metrics.stringWidth(value) <= maxWidth) {
            return value;
        }
        return ellipsis(value, metrics, maxWidth);
    }

    private static List<String> wrapWithLimit(String value, FontMetrics metrics, int maxWidth, int maxLines) {
        List<String> wrapped = wrap(value, metrics, maxWidth);
        if (wrapped.isEmpty()) {
            return List.of("");
        }
        if (wrapped.size() <= maxLines) {
            return wrapped;
        }
        List<String> kept = new ArrayList<>(wrapped.subList(0, maxLines));
        int lastIndex = kept.size() - 1;
        kept.set(lastIndex, ellipsis(kept.get(lastIndex), metrics, maxWidth));
        return kept;
    }

    private static List<String> wrap(String value, FontMetrics metrics, int maxWidth) {
        String text = value == null ? "" : value;
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String next = current.toString() + new String(Character.toChars(codePoint));
            if (metrics.stringWidth(next) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder();
            } else {
                current.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
                continue;
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String ellipsis(String text, FontMetrics metrics, int maxWidth) {
        String value = text == null ? "" : text;
        String suffix = "…";
        if (metrics.stringWidth(value + suffix) <= maxWidth) {
            return value + suffix;
        }
        StringBuilder builder = new StringBuilder(value);
        while (!builder.isEmpty() && metrics.stringWidth(builder + suffix) > maxWidth) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder + suffix;
    }

    private static Font loadFont(String path, int style) {
        try (InputStream input = ProjectPhotoWatermarkRenderer.class.getResourceAsStream(path)) {
            if (input == null) {
                throw BusinessException.badRequest(ProjectPhotoRules.RENDER_FAILED);
            }
            return Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(style);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(ProjectPhotoRules.RENDER_FAILED);
        }
    }

    private static byte[] writeJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw BusinessException.badRequest(ProjectPhotoRules.RENDER_FAILED);
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.92f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    static int jpegOrientation(byte[] bytes) {
        if (bytes.length < 4 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int offset = 2;
        while (offset + 4 < bytes.length) {
            if ((bytes[offset] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = bytes[offset + 1] & 0xFF;
            int length = ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
            if (marker == 0xE1 && length > 14) {
                return orientationInExif(bytes, offset + 4, length - 2);
            }
            if (length < 2) {
                return 1;
            }
            offset += 2 + length;
        }
        return 1;
    }

    private static int orientationInExif(byte[] bytes, int start, int length) {
        if (start + 6 >= bytes.length || bytes[start] != 'E' || bytes[start + 1] != 'x') {
            return 1;
        }
        int tiff = start + 6;
        if (tiff + 8 >= bytes.length || tiff + length > bytes.length) {
            return 1;
        }
        boolean little = bytes[tiff] == 'I';
        int ifd = tiff + readInt(bytes, tiff + 4, little);
        if (ifd + 2 >= bytes.length) {
            return 1;
        }
        int entries = readShort(bytes, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > bytes.length) {
                return 1;
            }
            if (readShort(bytes, entry, little) == 0x0112) {
                int value = readShort(bytes, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == 1) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            AffineTransform transform = new AffineTransform();
            switch (orientation) {
                case 2 -> {
                    transform.scale(-1, 1);
                    transform.translate(-width, 0);
                }
                case 3 -> {
                    transform.translate(width, height);
                    transform.rotate(Math.PI);
                }
                case 4 -> {
                    transform.scale(1, -1);
                    transform.translate(0, -height);
                }
                case 6 -> {
                    transform.translate(height, 0);
                    transform.rotate(Math.PI / 2);
                }
                case 8 -> {
                    transform.translate(0, width);
                    transform.rotate(-Math.PI / 2);
                }
                default -> {
                    return source;
                }
            }
            graphics.drawImage(source, transform, null);
            return target;
        } finally {
            graphics.dispose();
        }
    }

    private static int readShort(byte[] bytes, int offset, boolean little) {
        int first = bytes[offset] & 0xFF;
        int second = bytes[offset + 1] & 0xFF;
        return little ? first | (second << 8) : (first << 8) | second;
    }

    private static int readInt(byte[] bytes, int offset, boolean little) {
        int one = bytes[offset] & 0xFF;
        int two = bytes[offset + 1] & 0xFF;
        int three = bytes[offset + 2] & 0xFF;
        int four = bytes[offset + 3] & 0xFF;
        return little ? one | (two << 8) | (three << 16) | (four << 24)
                : (one << 24) | (two << 16) | (three << 8) | four;
    }

    private static final class FieldRow {
        private final String label;
        private final List<String> valueLines;

        private FieldRow(String label, List<String> valueLines) {
            this.label = label;
            this.valueLines = valueLines;
        }
    }
}
