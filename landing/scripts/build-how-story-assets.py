#!/usr/bin/env python3
"""Builds the #how story's photo-library mosaic sprite and its manifest.

Run from landing/ (needs Python 3 with Pillow and WebP support, nothing from npm):

    python3 scripts/build-how-story-assets.py

Outputs (both committed):
  public/assets/how/library-mosaic.webp  16 x 20 cells of 32px (a 30px tile and a 2px gutter), 512 x 640
  src/data/how-mosaic.json               { cols, rows, cell, sources, noLocation }

Every open-licence photo in public/assets/photos/ except gyeongbuk-01..04 (the four 경주 matches, which the
page draws as their own tiles) is cut into 4 crops, so the library reads as many different shots. The block
repeats across the window, so no photo may touch itself in any of the 8 neighbours, wrap-around included.
The output is deterministic: mulberry32 seeded 21474.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PHOTOS = ROOT / "public" / "assets" / "photos"
SPRITE = ROOT / "public" / "assets" / "how" / "library-mosaic.webp"
MANIFEST = ROOT / "src" / "data" / "how-mosaic.json"

SEED = 21474
COLS, ROWS = 16, 20
CELL, TILE = 32, 30
GUTTER = (14, 18, 20)  # #0E1214, the inward ground
QUALITY = 58
MAX_BYTES = 100 * 1024
# x%, y% and zoom of each crop, so one photo yields 4 different-looking tiles.
CROPS = [(50, 50, 1.15), (28, 34, 1.5), (72, 66, 1.6), (40, 70, 1.45)]
# About 1 in 9 cells has no stored location (no pin). They are not dimmed or labelled on the page.
NO_LOCATION_COUNT = 36


def mulberry32(seed: int):
    state = seed & 0xFFFFFFFF

    def next_value() -> float:
        nonlocal state
        state = (state + 0x6D2B79F5) & 0xFFFFFFFF
        t = state
        t = ((t ^ (t >> 15)) * (t | 1)) & 0xFFFFFFFF
        t ^= (t + (((t ^ (t >> 7)) * (t | 61)) & 0xFFFFFFFF)) & 0xFFFFFFFF
        return ((t ^ (t >> 14)) & 0xFFFFFFFF) / 4294967296

    return next_value


def shuffled(items, rng):
    items = list(items)
    for index in range(len(items) - 1, 0, -1):
        swap = int(rng() * (index + 1))
        items[index], items[swap] = items[swap], items[index]
    return items


def neighbours(index: int):
    row, col = divmod(index, COLS)
    for dr in (-1, 0, 1):
        for dc in (-1, 0, 1):
            if dr or dc:
                yield ((row + dr) % ROWS) * COLS + (col + dc) % COLS


def place(tiles, rng, attempts=200):
    """Fills every cell: each unique crop once, then repeats, never next to its own photo (toroidal)."""
    total = COLS * ROWS
    for _ in range(attempts):
        unique = shuffled(tiles, rng)
        repeats = shuffled(tiles, rng)
        pool = unique + repeats[: total - len(unique)]
        grid = [None] * total
        ok = True
        for cell in range(total):
            taken = {grid[n][0] for n in neighbours(cell) if grid[n] is not None}
            pick = next((i for i, tile in enumerate(pool) if tile[0] not in taken), None)
            if pick is None:
                ok = False
                break
            grid[cell] = pool.pop(pick)
        if ok:
            return grid
    raise SystemExit("could not place the mosaic without touching repeats")


def crop_tile(image: Image.Image, crop) -> Image.Image:
    x_pct, y_pct, zoom = crop
    width, height = image.size
    side = min(width, height) / zoom
    cx = min(max(width * x_pct / 100, side / 2), width - side / 2)
    cy = min(max(height * y_pct / 100, side / 2), height - side / 2)
    box = (round(cx - side / 2), round(cy - side / 2), round(cx + side / 2), round(cy + side / 2))
    return image.crop(box).resize((TILE, TILE), Image.LANCZOS)


def main() -> None:
    files = sorted(p.name for p in PHOTOS.glob("*.webp") if not p.name.startswith("gyeongbuk-"))
    if len(files) != 55:
        print(f"warning: expected 55 source photos, found {len(files)}", file=sys.stderr)
    rng = mulberry32(SEED)
    tiles = [(name, crop_index) for name in files for crop_index in range(len(CROPS))]
    grid = place(tiles, rng)

    sources = {name: Image.open(PHOTOS / name).convert("RGB") for name in files}
    sprite = Image.new("RGB", (COLS * CELL, ROWS * CELL), GUTTER)
    offset = (CELL - TILE) // 2
    for cell, (name, crop_index) in enumerate(grid):
        row, col = divmod(cell, COLS)
        sprite.paste(crop_tile(sources[name], CROPS[crop_index]), (col * CELL + offset, row * CELL + offset))

    no_location = sorted(shuffled(range(COLS * ROWS), rng)[:NO_LOCATION_COUNT])

    SPRITE.parent.mkdir(parents=True, exist_ok=True)
    sprite.save(SPRITE, "WEBP", quality=QUALITY, method=6)
    size = SPRITE.stat().st_size
    if size > MAX_BYTES:
        raise SystemExit(f"{SPRITE.name} is {size} bytes, over the {MAX_BYTES} budget")

    manifest = {
        "cols": COLS,
        "rows": ROWS,
        "cell": CELL,
        "seed": SEED,
        "sources": [name for name, _ in grid],
        "crops": [crop_index for _, crop_index in grid],
        "noLocation": no_location,
    }
    MANIFEST.write_text(json.dumps(manifest, separators=(",", ":")) + "\n", encoding="utf8")
    print(f"{SPRITE.relative_to(ROOT)}: {size} bytes, {len(files)} photos, {len(no_location)} no-location cells")


if __name__ == "__main__":
    main()
