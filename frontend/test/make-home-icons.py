import math
import struct
import zlib
from pathlib import Path


def chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path, width, height, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels(x, y))
    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    Path(path).write_bytes(data)


GRAY = (90, 99, 114)


def gear_filled(x, y):
    dx, dy = x - 48, y - 48
    radius = math.hypot(dx, dy)
    if radius < 14:
        return False
    turn = ((math.atan2(dy, dx) + math.pi) / math.tau * 8) % 1
    outer = 40 if 0.16 < turn < 0.84 else 30
    return radius <= outer


def stroke_of(filled, x, y, width=3.1, samples=20):
    if not filled(x, y):
        return 0
    outside = 0
    for index in range(samples):
        angle = math.tau * index / samples
        if not filled(x + math.cos(angle) * width, y + math.sin(angle) * width):
            outside += 1
    return outside / samples


def bust(x, y, cx, cy):
    head = abs(math.hypot(x - cx, y - (cy - 16)) - 9) <= 1.7
    dx = (x - cx) / 16
    dy = (y - (cy + 16)) / 13
    shoulder = abs(dx * dx + dy * dy - 1) <= 0.16 and cy - 2 <= y <= cy + 16
    return head or shoulder


def icon(width, height, sample, scale=4):
    def pixel(x, y):
        alpha = 0
        for oy in range(scale):
            for ox in range(scale):
                alpha += sample((x * scale + ox + 0.5) / scale, (y * scale + oy + 0.5) / scale)
        alpha = min(1, alpha / (scale * scale))
        return (GRAY[0], GRAY[1], GRAY[2], int(255 * alpha))

    return pixel


def gear_sample(x, y):
    return stroke_of(gear_filled, x, y)


def members_sample(x, y):
    back = 0.7 if bust(x, y, 34, 50) else 0
    front = 1 if bust(x, y, 58, 54) else 0
    return max(back, front)


root = Path(r'F:\01 Project\001_image-Collection-System\frontend\static\watermark')
root.mkdir(parents=True, exist_ok=True)
write_png(root / 'gear.png', 96, 96, icon(96, 96, gear_sample))
write_png(root / 'members.png', 96, 96, icon(96, 96, members_sample))
print('wrote', root)
