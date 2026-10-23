#!/usr/bin/env python3
"""Convert the 1992 Mah Jongg MEDIEVAL.TIS 4-plane tiles into Android PNG resources."""
from pathlib import Path
import re
import sys
import zipfile
from io import BytesIO
from urllib.request import urlopen

URL = "https://archive.org/download/win3_MAHJONGG/win3_MAHJONGG.zip"
OUT = Path("app/src/main/res/drawable-nodpi")
TILE_W, TILE_H = 48, 60
RECORD_SIZE = 1446
PIXEL_BYTES = 1440
TILE_COUNT = 44

def get_tis():
    local = Path("MEDIEVAL.TIS")
    if local.exists():
        return local.read_bytes()
    print("Downloading original Mah Jongg archive from Internet Archive...")
    raw = urlopen(URL, timeout=45).read()
    with zipfile.ZipFile(BytesIO(raw)) as outer:
        direct = [n for n in outer.namelist() if n.upper().endswith("MEDIEVAL.TIS")]
        if direct:
            return outer.read(direct[0])
        nested = [n for n in outer.namelist() if n.upper().endswith(".ZIP")]
        for name in nested:
            try:
                with zipfile.ZipFile(BytesIO(outer.read(name))) as inner:
                    candidates = [n for n in inner.namelist() if n.upper().endswith("MEDIEVAL.TIS")]
                    if candidates:
                        return inner.read(candidates[0])
            except zipfile.BadZipFile:
                continue
    raise RuntimeError("MEDIEVAL.TIS was not found in the Internet Archive item")

