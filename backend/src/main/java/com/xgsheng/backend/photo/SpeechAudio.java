package com.xgsheng.backend.photo;

import com.xgsheng.backend.exception.BusinessException;

import java.util.Locale;

/**
 * 只接受 mp3、aac、wav，并且时长不超过 60 秒、体积不超过 3MB。
 */
public final class SpeechAudio {

    public static final int MAX_BYTES = 3 * 1024 * 1024;
    public static final long MAX_DURATION_MILLIS = 60_000L;

    private SpeechAudio() {
    }

    public static String format(String filename, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw BusinessException.badRequest(PreviewMessages.AUDIO_TOO_LARGE);
        }
        String named = extension(filename);
        if (named != null && named.isEmpty()) {
            throw BusinessException.badRequest(PreviewMessages.AUDIO_TOO_LARGE);
        }
        String format = named == null ? contentType(contentType) : named;
        long duration = durationMillis(format, bytes);
        if (format == null || duration < 0 || duration > MAX_DURATION_MILLIS) {
            throw BusinessException.badRequest(PreviewMessages.AUDIO_TOO_LARGE);
        }
        return format;
    }

    static long durationMillis(String format, byte[] bytes) {
        if ("wav".equals(format)) {
            return wav(bytes);
        }
        if ("mp3".equals(format)) {
            return mp3(bytes);
        }
        if ("aac".equals(format)) {
            return aac(bytes);
        }
        return -1;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = filename.substring(slash + 1).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        String value = name.substring(dot + 1);
        if ("mp3".equals(value) || "aac".equals(value) || "wav".equals(value)) {
            return value;
        }
        return "";
    }

    private static String contentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String value = contentType.toLowerCase(Locale.ROOT);
        if (value.contains("mpeg") || value.contains("mp3")) {
            return "mp3";
        }
        if (value.contains("wav")) {
            return "wav";
        }
        if (value.contains("aac")) {
            return "aac";
        }
        return null;
    }

    private static long wav(byte[] bytes) {
        if (bytes.length < 44 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) {
            return -1;
        }
        Integer sampleRate = null;
        Integer channels = null;
        Integer bits = null;
        Integer dataSize = null;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunk = text(bytes, offset, 4);
            int size = integer(bytes, offset + 4);
            int start = offset + 8;
            if (size < 0 || start > bytes.length) {
                return -1;
            }
            if ("fmt ".equals(chunk) && size >= 16 && start + 16 <= bytes.length) {
                channels = shortValue(bytes, start + 2);
                sampleRate = integer(bytes, start + 4);
                bits = shortValue(bytes, start + 14);
            } else if ("data".equals(chunk)) {
                dataSize = Math.min(size, bytes.length - start);
                break;
            }
            offset = start + size + (size % 2);
        }
        if (sampleRate == null || sampleRate <= 0 || channels == null || channels <= 0
                || bits == null || bits < 8 || bits % 8 != 0 || dataSize == null) {
            return -1;
        }
        long bytesPerSecond = (long) sampleRate * channels * (bits / 8);
        if (bytesPerSecond <= 0) {
            return -1;
        }
        return dataSize * 1000L / bytesPerSecond;
    }

    private static long mp3(byte[] bytes) {
        int offset = id3Size(bytes);
        long duration = 0;
        boolean found = false;
        while (offset + 4 <= bytes.length) {
            int first = bytes[offset] & 0xFF;
            int second = bytes[offset + 1] & 0xFF;
            if (first != 0xFF || (second & 0xE0) != 0xE0) {
                offset++;
                continue;
            }
            int version = (second >> 3) & 0x3;
            int layer = (second >> 1) & 0x3;
            int third = bytes[offset + 2] & 0xFF;
            int bitrateIndex = (third >> 4) & 0xF;
            int sampleIndex = (third >> 2) & 0x3;
            int padding = (third >> 1) & 0x1;
            if (layer != 1 || version == 1 || bitrateIndex == 0 || bitrateIndex == 15 || sampleIndex == 3) {
                offset++;
                continue;
            }
            int bitrate = bitrate(version, bitrateIndex);
            int sampleRate = sampleRate(version, sampleIndex);
            if (bitrate <= 0 || sampleRate <= 0) {
                offset++;
                continue;
            }
            int samples = version == 3 ? 1152 : 576;
            int coefficient = version == 3 ? 144 : 72;
            int frameLength = coefficient * bitrate * 1000 / sampleRate + padding;
            if (frameLength < 4 || offset + frameLength > bytes.length) {
                break;
            }
            duration += samples * 1000L / sampleRate;
            found = true;
            if (duration > MAX_DURATION_MILLIS) {
                return duration;
            }
            offset += frameLength;
        }
        return found ? duration : -1;
    }

    private static long aac(byte[] bytes) {
        int offset = 0;
        long duration = 0;
        boolean found = false;
        int[] rates = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
        while (offset + 7 <= bytes.length) {
            int first = bytes[offset] & 0xFF;
            int second = bytes[offset + 1] & 0xFF;
            if (first != 0xFF || (second & 0xF0) != 0xF0) {
                offset++;
                continue;
            }
            int rateIndex = (bytes[offset + 2] & 0x3C) >> 2;
            if (rateIndex >= rates.length) {
                offset++;
                continue;
            }
            int frameLength = ((bytes[offset + 3] & 0x03) << 11)
                    | ((bytes[offset + 4] & 0xFF) << 3)
                    | ((bytes[offset + 5] & 0xE0) >> 5);
            if (frameLength < 7 || offset + frameLength > bytes.length) {
                break;
            }
            duration += 1024L * 1000L / rates[rateIndex];
            found = true;
            if (duration > MAX_DURATION_MILLIS) {
                return duration;
            }
            offset += frameLength;
        }
        return found ? duration : -1;
    }

    private static int id3Size(byte[] bytes) {
        if (bytes.length < 10 || !ascii(bytes, 0, "ID3")) {
            return 0;
        }
        int size = ((bytes[6] & 0x7F) << 21) | ((bytes[7] & 0x7F) << 14) | ((bytes[8] & 0x7F) << 7) | (bytes[9] & 0x7F);
        int offset = 10 + size;
        return Math.min(offset, bytes.length);
    }

    private static int bitrate(int version, int index) {
        int[] mpeg1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
        int[] mpeg2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
        return version == 3 ? mpeg1[index] : mpeg2[index];
    }

    private static int sampleRate(int version, int index) {
        int[] mpeg1 = {44100, 48000, 32000};
        int[] mpeg2 = {22050, 24000, 16000};
        int[] mpeg25 = {11025, 12000, 8000};
        if (version == 3) {
            return mpeg1[index];
        }
        if (version == 2) {
            return mpeg2[index];
        }
        return mpeg25[index];
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        return value.equals(text(bytes, offset, value.length()));
    }

    private static String text(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int integer(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static int shortValue(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }
}
