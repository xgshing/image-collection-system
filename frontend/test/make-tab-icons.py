import struct
import zlib
from pathlib import Path


def chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)


def png(width, height, pixel):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixel(x, y))
    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')


def inside_round_rect(px, py, x0, y0, x1, y1, radius):
    if px < x0 or px > x1 or py < y0 or py > y1:
        return False
    cx = min(max(px, x0 + radius), x1 - radius)
    cy = min(max(py, y0 + radius), y1 - radius)
    return (px - cx) ** 2 + (py - cy) ** 2 <= radius ** 2


def camera(active):
    color = (121, 101, 216, 255) if active else (125, 133, 146, 255)
    scale = 4

    def cover(x, y):
        px, py = x / scale, y / scale
        body = inside_round_rect(px, py, 18, 30, 63, 60, 7)
        top = inside_round_rect(px, py, 30, 22, 46, 34, 3)
        lens = (px - 40.5) ** 2 + (py - 45) ** 2 <= 11 ** 2
        hole = (px - 40.5) ** 2 + (py - 45) ** 2 <= 5.2 ** 2
        if hole:
            return (0, 0, 0, 0)
        if body or top or lens:
            return color
        return (0, 0, 0, 0)

    def sample(x, y):
        totals = [0, 0, 0, 0]
        for oy in range(scale):
            for ox in range(scale):
                pixel = cover(x * scale + ox, y * scale + oy)
                for index, value in enumerate(pixel):
                    totals[index] += value
        count = scale * scale
        return tuple(value // count for value in totals)

    return png(81, 81, sample)


root = Path(r'F:\01 Project\001_image-Collection-System\frontend\static\tabbar')
(root / 'camera.png').write_bytes(camera(False))
(root / 'camera-active.png').write_bytes(camera(True))
print('camera icons written', (root / 'camera.png').stat().st_size, (root / 'camera-active.png').stat().st_size)
