#!/usr/bin/env python3
"""Decode the original 1992 MEDIEVAL.TIS artwork into Android drawable resources."""
from pathlib import Path
from io import BytesIO
from urllib.request import urlopen
import re
import zipfile

URL = "https://archive.org/download/win3_MAHJONGG/MAHJONGG.ZIP"
OUT = Path("app/src/main/res/drawable-nodpi")
WIDTH, HEIGHT, TILE_COUNT, RECORD_SIZE = 48, 60, 44, 1446

def load_tis():
    local = Path("MEDIEVAL.TIS")
    if local.exists():
        return local.read_bytes()
    print("Downloading the original game archive from Internet Archive", flush=True)
    archive = urlopen(URL, timeout=60).read()
    with zipfile.ZipFile(BytesIO(archive)) as outer:
        files = outer.namelist()
        for name in files:
            if name.upper().endswith("MEDIEVAL.TIS"):
                return outer.read(name)
        for name in files:
            if not name.upper().endswith(".ZIP"):
                continue
            try:
                with zipfile.ZipFile(BytesIO(outer.read(name))) as inner:
                    for member in inner.namelist():
                        if member.upper().endswith("MEDIEVAL.TIS"):
                            return inner.read(member)
            except zipfile.BadZipFile:
                pass
    raise RuntimeError("Could not find MEDIEVAL.TIS in the downloaded archive")

def read_palette(data):
    tail = data[32 + TILE_COUNT * RECORD_SIZE:]
    marker = tail.rfind(bytes((0x77, 0x02)))
    if marker >= 0:
        tail = tail[marker + 2:]
    matches = re.findall(rb"(?m)^\s*(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})\s*\r?$", tail)
    if len(matches) < 15:
        raise RuntimeError(f"Could not parse palette; found {len(matches)} RGB entries")
    colors = [tuple(round(int(v) * 255 / 63) for v in rgb) + (255,) for rgb in matches[-16:]]
    while len(colors) < 16:
        colors.append((255, 255, 255, 255))
    return colors

def main():
    try:
        from PIL import Image
    except ImportError as exc:
        raise RuntimeError("Pillow is required to decode the original tile art") from exc
    data = load_tis()
    minimum = 32 + TILE_COUNT * RECORD_SIZE
    if len(data) < minimum:
        raise RuntimeError(f"MEDIEVAL.TIS is truncated ({len(data)} bytes)")
    palette = read_palette(data)
    OUT.mkdir(parents=True, exist_ok=True)
    for tile in range(TILE_COUNT):
        start = 32 + tile * RECORD_SIZE + 6
        planes = data[start:start + 1440]
        if len(planes) != 1440:
            raise RuntimeError(f"Tile {tile} is truncated")
        pixels = []
        # Four 1-bit planes are interleaved per scanline: 4 × 6 bytes × 60 rows.
        for y in range(HEIGHT):
            row = planes[y * 24:(y + 1) * 24]
            for x in range(WIDTH):
                byte_index = x // 8
                mask = 1 << (7 - x % 8)
                index = 0
                for plane in range(4):
                    if row[plane * 6 + byte_index] & mask:
                        index |= 1 << plane
                pixels.append(palette[index])
        image = Image.new("RGBA", (WIDTH, HEIGHT))
        image.putdata(pixels)
        image.save(OUT / f"medieval_{tile:02d}.png", optimize=True)
    print(f"Generated {TILE_COUNT} original medieval tile images in {OUT}", flush=True)

if __name__ == "__main__":
    main()
