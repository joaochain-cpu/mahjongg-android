#!/usr/bin/env python3
"""Convert the 1992 Mah Jongg MEDIEVAL.TIS 4-plane tiles into Android PNG resources."""
from pathlib import Path
import re
import sys
import zipfile
from io import BytesIO
from urllib.request import urlopen

URL = "https://archive.org/download/win3_MAHJONGG/MAHJONGG.ZIP"
OUT = Path("app/src/main/res/drawable-nodpi")
TILE_W, TILE_H = 48, 60
RECORD_SIZE = 1446
PIXEL_BYTES = 1440
TILE_COUNT = 44

def get_tis():
    # Allow a local file for repeatable debugging; CI downloads the original shareware archive.
    local = Path("MEDIEVAL.TIS")
    if local.exists():
        return local.read_bytes()
    print("Downloading original Mah Jongg archive from Internet Archive...")
    raw = urlopen(URL, timeout=45).read()
    with zipfile.ZipFile(BytesIO(raw)) as outer:
        candidates = [n for n in outer.namelist() if n.upper().endswith("MAHJONGG.ZIP")]
        if not candidates:
            raise RuntimeError("MAHJONGG.ZIP was not found in the Archive.org download")
        inner_bytes = outer.read(candidates[0])
    with zipfile.ZipFile(BytesIO(inner_bytes)) as inner:
        candidates = [n for n in inner.namelist() if n.upper().endswith("MEDIEVAL.TIS")]
        if not candidates:
            raise RuntimeError("MEDIEVAL.TIS was not found in the original game archive")
        return inner.read(candidates[0])

def palette_from(data):
    # The original file stores a 16-color VGA palette as ASCII RGB triples at the end.
    tail = data[32 + TILE_COUNT * RECORD_SIZE:]
    # The final palette block is prefixed by the legacy marker bytes 0x77, 0x02.
    marker = tail.rfind(b"w\\x02")
    if marker >= 0:
        tail = tail[marker + 2:]
    triples = re.findall(rb"(?m)^\s*(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})\s*\r?$", tail)
    if len(triples) < 16:
        raise RuntimeError(f"Could not parse 16-color palette (found {len(triples)} entries)")
    vals = triples[-16:]
    return [tuple(round(int(v) * 255 / 63) for v in rgb) + (255,) for rgb in vals]

def main():
    try:
        from PIL import Image
    except ImportError:
        raise RuntimeError("Pillow is required; install it with: python -m pip install Pillow")
    data = get_tis()
    if len(data) < 32 + TILE_COUNT * RECORD_SIZE:
        raise RuntimeError(f"MEDIEVAL.TIS is too short: {len(data)} bytes")
    palette = palette_from(data)
    OUT.mkdir(parents=True, exist_ok=True)
    for i in range(TILE_COUNT):
        start = 32 + i * RECORD_SIZE + 6
        planes = data[start:start + PIXEL_BYTES]
        if len(planes) != PIXEL_BYTES:
            raise RuntimeError(f"Tile {i} is truncated")
        pixels = []
        # TIS stores four 1-bit planes interleaved within each 48-pixel scanline:
        # 4 planes × 6 bytes per plane × 60 rows = 1440 bytes.
        for y in range(TILE_H):
            row = planes[y * 24:(y + 1) * 24]
            for x in range(TILE_W):
                byte_index = x // 8
                mask = 1 << (7 - (x % 8))
                idx = 0
                for plane in range(4):
                    if row[plane * 6 + byte_index] & mask:
                        idx |= 1 << plane
                pixels.append(palette[idx])
        image = Image.new("RGBA", (TILE_W, TILE_H))
        image.putdata(pixels)
        image.save(OUT / f"medieval_{i:02d}.png", optimize=True)
    print(f"Generated {TILE_COUNT} medieval tile PNGs in {OUT}")

if __name__ == "__main__":
    main()
