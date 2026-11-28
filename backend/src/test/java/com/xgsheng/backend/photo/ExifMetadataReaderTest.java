package com.xgsheng.backend.photo;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExifMetadataReaderTest {

    @Test
    void readsDateTimeOriginalInLittleEndianWithoutOffsetAsShanghai() {
        byte[] jpeg = buildExifJpeg(ByteOrder.LITTLE_ENDIAN, "2026:11:28 16:30:05", null);
        assertEquals("2026-11-28T08:30:05Z", ExifMetadataReader.readTakenAtIso(jpeg));
    }

    @Test
    void readsDateTimeOriginalInBigEndianWithOffset() {
        byte[] jpeg = buildExifJpeg(ByteOrder.BIG_ENDIAN, "2026:11:28 10:15:00", "+09:00");
        assertEquals("2026-11-28T01:15:00Z", ExifMetadataReader.readTakenAtIso(jpeg));
    }

    @Test
    void returnsNullForNonJpegOrMissingExif() {
        assertNull(ExifMetadataReader.readTakenAtIso(new byte[]{1, 2, 3, 4}));
        assertNull(ExifMetadataReader.readTakenAtIso(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}));
    }

    private static byte[] buildExifJpeg(ByteOrder order, String dateTimeOriginal, String offsetOriginal) {
        byte[] dateBytes = (dateTimeOriginal + "\0").getBytes(StandardCharsets.US_ASCII);
        byte[] offsetBytes = offsetOriginal == null ? null : (offsetOriginal + "\0").getBytes(StandardCharsets.US_ASCII);

        // TIFF layout:
        // 0..7: header (8)
        // 8..25: IFD0 (2 + 1*12 + 4 = 18) -> offset 26
        int ifd0Offset = 8;
        int exifIfdOffset = 26;
        int exifCount = offsetBytes == null ? 1 : 2;
        int dataOffset = exifIfdOffset + 2 + exifCount * 12 + 4;
        int dateDataOffset = dataOffset;
        int offsetDataOffset = dateDataOffset + dateBytes.length;
        int tiffLength = offsetDataOffset + (offsetBytes == null ? 0 : offsetBytes.length);

        ByteBuffer tiff = ByteBuffer.allocate(tiffLength).order(order);
        if (order == ByteOrder.LITTLE_ENDIAN) {
            tiff.put((byte) 'I').put((byte) 'I');
        } else {
            tiff.put((byte) 'M').put((byte) 'M');
        }
        tiff.putShort((short) 0x002A);
        tiff.putInt(ifd0Offset);

        // IFD0: 1 entry -> ExifIFDPointer (0x8769)
        tiff.position(ifd0Offset);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x8769);
        tiff.putShort((short) 4); // LONG
        tiff.putInt(1);
        tiff.putInt(exifIfdOffset);
        tiff.putInt(0);

        // ExifIFD
        tiff.position(exifIfdOffset);
        tiff.putShort((short) exifCount);
        tiff.putShort((short) 0x9003);
        tiff.putShort((short) 2); // ASCII
        tiff.putInt(dateBytes.length);
        tiff.putInt(dateDataOffset);
        if (offsetBytes != null) {
            tiff.putShort((short) 0x9011);
            tiff.putShort((short) 2); // ASCII
            tiff.putInt(offsetBytes.length);
            tiff.putInt(offsetDataOffset);
        }
        tiff.putInt(0);

        tiff.position(dateDataOffset);
        tiff.put(dateBytes);
        if (offsetBytes != null) {
            tiff.position(offsetDataOffset);
            tiff.put(offsetBytes);
        }

        byte[] tiffBytes = tiff.array();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(0xFF);
        out.write(0xE1);
        int segLen = 2 + 6 + tiffBytes.length;
        out.write((segLen >> 8) & 0xFF);
        out.write(segLen & 0xFF);
        out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(tiffBytes);
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
    }
}
