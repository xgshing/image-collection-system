package com.xgsheng.backend.photo;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 读取 JPEG 照片 EXIF 中的拍摄时间（DateTimeOriginal / DateTimeDigitized / DateTime）。
 * 只做内存字节解析，不引入外部依赖，损坏或非 JPEG 文件静默返回空。
 */
public final class ExifMetadataReader {

    private static final int TAG_DATE_TIME = 0x0132;
    private static final int TAG_EXIF_IFD_POINTER = 0x8769;
    private static final int TAG_DATE_TIME_ORIGINAL = 0x9003;
    private static final int TAG_DATE_TIME_DIGITIZED = 0x9004;
    private static final int TAG_OFFSET_TIME = 0x9010;
    private static final int TAG_OFFSET_TIME_ORIGINAL = 0x9011;

    private static final DateTimeFormatter EXIF_DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private ExifMetadataReader() {
    }

    public static Optional<Instant> readTakenAt(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
            return Optional.empty();
        }
        int offset = 2;
        while (offset + 4 <= bytes.length) {
            while (offset < bytes.length && (bytes[offset] & 0xFF) == 0xFF) {
                offset++;
            }
            if (offset >= bytes.length) {
                break;
            }
            int marker = bytes[offset] & 0xFF;
            offset++;
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (offset + 2 > bytes.length) {
                break;
            }
            int segmentLength = ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
            if (segmentLength < 2 || offset + segmentLength > bytes.length) {
                break;
            }
            if (marker == 0xE1 && segmentLength > 8) {
                Optional<Instant> found = parseApp1(bytes, offset + 2, segmentLength - 2);
                if (found.isPresent()) {
                    return found;
                }
            }
            offset += segmentLength;
        }
        return Optional.empty();
    }

    public static String readTakenAtIso(byte[] bytes) {
        return readTakenAt(bytes).map(Instant::toString).orElse(null);
    }

    private static Optional<Instant> parseApp1(byte[] bytes, int start, int length) {
        if (length < 14 || start + length > bytes.length) {
            return Optional.empty();
        }
        if (bytes[start] != 'E' || bytes[start + 1] != 'x' || bytes[start + 2] != 'i'
                || bytes[start + 3] != 'f' || bytes[start + 4] != 0 || bytes[start + 5] != 0) {
            return Optional.empty();
        }
        int tiff = start + 6;
        int tiffEnd = start + length;
        if (tiff + 8 > tiffEnd) {
            return Optional.empty();
        }
        boolean little;
        if (bytes[tiff] == 'I' && bytes[tiff + 1] == 'I') {
            little = true;
        } else if (bytes[tiff] == 'M' && bytes[tiff + 1] == 'M') {
            little = false;
        } else {
            return Optional.empty();
        }
        if (readShort(bytes, tiff + 2, little) != 0x002A) {
            return Optional.empty();
        }
        long ifd0Rel = readUnsignedInt(bytes, tiff + 4, little);
        if (ifd0Rel < 8 || tiff + ifd0Rel + 2 > tiffEnd) {
            return Optional.empty();
        }
        int ifd0 = (int) (tiff + ifd0Rel);
        String ifd0DateTime = null;
        long exifIfdRel = -1;

        int entries = readShort(bytes, ifd0, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd0 + 2 + i * 12;
            if (entry + 12 > tiffEnd) {
                break;
            }
            int tag = readShort(bytes, entry, little);
            if (tag == TAG_EXIF_IFD_POINTER) {
                exifIfdRel = readUnsignedInt(bytes, entry + 8, little);
            } else if (tag == TAG_DATE_TIME) {
                ifd0DateTime = readAsciiValue(bytes, tiff, tiffEnd, entry, little);
            }
        }

        String dateTimeOriginal = null;
        String dateTimeDigitized = null;
        String offsetOriginal = null;
        String offsetTime = null;

        if (exifIfdRel >= 8 && tiff + exifIfdRel + 2 <= tiffEnd) {
            int exifIfd = (int) (tiff + exifIfdRel);
            int exifEntries = readShort(bytes, exifIfd, little);
            for (int i = 0; i < exifEntries; i++) {
                int entry = exifIfd + 2 + i * 12;
                if (entry + 12 > tiffEnd) {
                    break;
                }
                int tag = readShort(bytes, entry, little);
                if (tag == TAG_DATE_TIME_ORIGINAL) {
                    dateTimeOriginal = readAsciiValue(bytes, tiff, tiffEnd, entry, little);
                } else if (tag == TAG_DATE_TIME_DIGITIZED) {
                    dateTimeDigitized = readAsciiValue(bytes, tiff, tiffEnd, entry, little);
                } else if (tag == TAG_OFFSET_TIME_ORIGINAL) {
                    offsetOriginal = readAsciiValue(bytes, tiff, tiffEnd, entry, little);
                } else if (tag == TAG_OFFSET_TIME) {
                    offsetTime = readAsciiValue(bytes, tiff, tiffEnd, entry, little);
                }
            }
        }

        String rawDate = firstNonBlank(dateTimeOriginal, dateTimeDigitized, ifd0DateTime);
        if (rawDate == null) {
            return Optional.empty();
        }
        String rawOffset = firstNonBlank(offsetOriginal, offsetTime);
        return parseExifInstant(rawDate, rawOffset);
    }

    private static Optional<Instant> parseExifInstant(String rawDate, String rawOffset) {
        String normalized = rawDate.trim();
        if (normalized.length() >= 19) {
            normalized = normalized.substring(0, 19);
        }
        LocalDateTime local;
        try {
            local = LocalDateTime.parse(normalized, EXIF_DATE_TIME);
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
        if (rawOffset != null) {
            try {
                ZoneOffset zoneOffset = ZoneOffset.of(rawOffset.trim());
                return Optional.of(local.atOffset(zoneOffset).toInstant());
            } catch (Exception ignored) {
                // fallback to default zone
            }
        }
        return Optional.of(local.atZone(DEFAULT_ZONE).toInstant());
    }

    private static String readAsciiValue(byte[] bytes, int tiff, int tiffEnd, int entry, boolean little) {
        int type = readShort(bytes, entry + 2, little);
        if (type != 2) {
            return null;
        }
        long count = readUnsignedInt(bytes, entry + 4, little);
        if (count <= 0 || count > 64) {
            return null;
        }
        int len = (int) count;
        int dataStart;
        if (len <= 4) {
            dataStart = entry + 8;
        } else {
            long rel = readUnsignedInt(bytes, entry + 8, little);
            if (rel < 0 || tiff + rel + len > tiffEnd) {
                return null;
            }
            dataStart = (int) (tiff + rel);
        }
        if (dataStart + len > tiffEnd) {
            return null;
        }
        int actualLen = len;
        while (actualLen > 0 && bytes[dataStart + actualLen - 1] == 0) {
            actualLen--;
        }
        if (actualLen <= 0) {
            return null;
        }
        return new String(bytes, dataStart, actualLen, StandardCharsets.US_ASCII);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int readShort(byte[] bytes, int offset, boolean little) {
        int first = bytes[offset] & 0xFF;
        int second = bytes[offset + 1] & 0xFF;
        return little ? first | (second << 8) : (first << 8) | second;
    }

    private static long readUnsignedInt(byte[] bytes, int offset, boolean little) {
        long one = bytes[offset] & 0xFFL;
        long two = bytes[offset + 1] & 0xFFL;
        long three = bytes[offset + 2] & 0xFFL;
        long four = bytes[offset + 3] & 0xFFL;
        return little ? one | (two << 8) | (three << 16) | (four << 24)
                : (one << 24) | (two << 16) | (three << 8) | four;
    }
}
