#!/usr/bin/env python3
"""Builds the #how story's photo-library mosaic sprite and its manifest.

Run from landing/ (needs Python 3 with Pillow and WebP support, nothing from npm):

    python3 scripts/build-how-story-assets.py

Outputs (all committed):
  public/assets/how/library-mosaic.webp  16 x 20 cells of 32px (a 30px tile and a 2px gutter), 512 x 640
  public/assets/how/library-pins.webp    the white location pins of the same block (transparent elsewhere)
  src/data/how-mosaic.json               { cols, rows, cell, sources, noLocation, panels }

Every open-licence photo in public/assets/photos/ except gyeongbuk-01..04 (the four 경주 matches, which the
page draws as their own tiles) is cut into 4 crops, so the library reads as many different shots. The block
repeats across the window, so no photo may touch itself in any of the 8 neighbours, wrap-around included.
The output is deterministic: mulberry32 seeded 21474.

The pins are a raster of their own, laid out like the sprite (same cells, same panels), so the page shows them as
one more background image instead of an SVG pattern of a few hundred pins, which cost Chromium a long GPU raster
the first time the library came into view. A cell in noLocation gets no pin.

The page never tiles the block as one repeating wallpaper (that reads as a lattice). It lays panels of
8 x 10 cells over the library window, each showing a different part of the block (`panels.offsets`, in cells),
chosen so that no photo touches itself across a panel seam either and nearby panels share few cells.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PHOTOS = ROOT / "public" / "assets" / "photos"
SPRITE = ROOT / "public" / "assets" / "how" / "library-mosaic.webp"
PINS = ROOT / "public" / "assets" / "how" / "library-pins.webp"
MANIFEST = ROOT / "src" / "data" / "how-mosaic.json"

SEED = 21474
COLS, ROWS = 16, 20
CELL, TILE = 32, 30
GUTTER = (14, 18, 20)  # #0E1214, the inward ground
QUALITY = 58
MAX_BYTES = 100 * 1024
PINS_MAX_BYTES = 30 * 1024
# The white pin, in the page's 16px cell: a 6 x 8 teardrop 2px in from the cell's top-left, a 1px darker rim and a
# 1px drop shadow. Drawn at 4x the sprite's scale and reduced, for smooth edges.
PIN_INSET = 2
PIN_FILL = (255, 255, 255, 0.92)
PIN_RIM = (0, 0, 0, 0.25)
PIN_SHADOW = (0, 0, 0, 0.28)
SUPERSAMPLE = 4
# x%, y% and zoom of each crop, so one photo yields 4 different-looking tiles.
CROPS = [(50, 50, 1.15), (28, 34, 1.5), (72, 66, 1.6), (40, 70, 1.45)]
# About 1 in 9 cells has no stored location (no pin). They are not dimmed or labelled on the page.
NO_LOCATION_COUNT = 36
# Panels of the block laid over the library window: 8 x 10 cells each, 5 across and 8 down, which covers the
# widest window (600px at 18px cells, 1300px tall) with room to spare.
PANEL_COLS, PANEL_ROWS = 8, 10
PANELS_ACROSS, PANELS_DOWN = 5, 8
# Neighbouring panels (8 around) may share at most 30% of their cells; panels two apart at most 60%.
PANEL_MAX_SHARED = {1: 0.3, 2: 0.6}


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


def panel_source(grid, offsets, col: int, row: int):
    """The source photo shown at a window cell, given each panel's offset into the block."""
    panel = (row // PANEL_ROWS) * PANELS_ACROSS + col // PANEL_COLS
    ox, oy = offsets[panel]
    block_col = (ox + col % PANEL_COLS) % COLS
    block_row = (oy + row % PANEL_ROWS) % ROWS
    return grid[block_row * COLS + block_col][0]


def shared_share(a, b) -> float:
    """Share of cells two panels have in common (both are windows into the same toroidal block)."""
    def overlap(delta: int, size: int, period: int) -> int:
        d = delta % period
        return max(0, size - d) + max(0, size - (period - d))
    return overlap(a[0] - b[0], PANEL_COLS, COLS) * overlap(a[1] - b[1], PANEL_ROWS, ROWS) / (PANEL_COLS * PANEL_ROWS)


def place_panels(grid, rng, attempts=400):
    """Picks every panel's offset: no photo beside itself across a seam, nearby panels mostly different."""
    count = PANELS_ACROSS * PANELS_DOWN
    width, height = PANELS_ACROSS * PANEL_COLS, PANELS_DOWN * PANEL_ROWS
    for _ in range(attempts):
        offsets = []
        for panel in range(count):
            prow, pcol = divmod(panel, PANELS_ACROSS)
            candidates = shuffled([(x, y) for x in range(COLS) for y in range(ROWS)], rng)
            chosen = None
            for candidate in candidates:
                trial = offsets + [candidate] + [(0, 0)] * (count - panel - 1)
                apart = lambda q: max(abs(q // PANELS_ACROSS - prow), abs(q % PANELS_ACROSS - pcol))
                if any(shared_share(candidate, offsets[q]) > PANEL_MAX_SHARED[apart(q)]
                       for q in range(panel) if apart(q) in PANEL_MAX_SHARED):
                    continue
                # Seams with the panels already placed (left, above, both diagonals above).
                ok = True
                x0, y0 = pcol * PANEL_COLS, prow * PANEL_ROWS
                for row in range(y0, y0 + PANEL_ROWS):
                    for col in range(x0, x0 + PANEL_COLS):
                        if row not in (y0, y0 + PANEL_ROWS - 1) and col not in (x0, x0 + PANEL_COLS - 1):
                            continue
                        here = panel_source(grid, trial, col, row)
                        for dr in (-1, 0, 1):
                            for dc in (-1, 0, 1):
                                r, c = row + dr, col + dc
                                if (dr or dc) and 0 <= r < height and 0 <= c < width:
                                    q = (r // PANEL_ROWS) * PANELS_ACROSS + c // PANEL_COLS
                                    if q < panel and panel_source(grid, trial, c, r) == here:
                                        ok = False
                if ok:
                    chosen = candidate
                    break
            if chosen is None:
                break
            offsets.append(chosen)
        if len(offsets) == count:
            return offsets
    raise SystemExit("could not place the mosaic panels")


def crop_tile(image: Image.Image, crop) -> Image.Image:
    x_pct, y_pct, zoom = crop
    width, height = image.size
    side = min(width, height) / zoom
    cx = min(max(width * x_pct / 100, side / 2), width - side / 2)
    cy = min(max(height * y_pct / 100, side / 2), height - side / 2)
    box = (round(cx - side / 2), round(cy - side / 2), round(cx + side / 2), round(cy + side / 2))
    return image.crop(box).resize((TILE, TILE), Image.LANCZOS)


def pin_outline(steps: int = 24):
    """The pin path (M3 7.6 C2.2 6.7 0.5 4.8 0.5 3.1 a2.5 2.5 0 0 1 5 0 c0 1.7 -1.7 3.6 -2.5 4.5 z) as points."""
    def cubic(p0, p1, p2, p3):
        out = []
        for i in range(1, steps + 1):
            t = i / steps
            mt = 1 - t
            out.append(tuple(mt ** 3 * a + 3 * mt * mt * t * b + 3 * mt * t * t * c + t ** 3 * d for a, b, c, d in zip(p0, p1, p2, p3)))
        return out
    import math
    points = [(3.0, 7.6)]
    points += cubic((3.0, 7.6), (2.2, 6.7), (0.5, 4.8), (0.5, 3.1))
    # The upper half circle around (3, 3.1), radius 2.5, from the left point over the top to the right.
    for i in range(1, steps * 2 + 1):
        angle = math.pi + math.pi * i / (steps * 2)
        points.append((3 + 2.5 * math.cos(angle), 3.1 + 2.5 * math.sin(angle)))
    points += cubic((5.5, 3.1), (5.5, 4.8), (3.8, 6.7), (3.0, 7.6))
    return points


def build_pins(no_location) -> Image.Image:
    """Every located cell's white pin, at the sprite's scale (32px cells for the page's 16px)."""
    from PIL import ImageDraw
    scale = CELL / 16 * SUPERSAMPLE
    size = (COLS * CELL * SUPERSAMPLE, ROWS * CELL * SUPERSAMPLE)
    outline = pin_outline()
    masks = {name: Image.new("L", size, 0) for name in ("shadow", "fill", "rim")}
    draws = {name: ImageDraw.Draw(mask) for name, mask in masks.items()}
    skip = set(no_location)
    for cell in range(COLS * ROWS):
        if cell in skip:
            continue
        row, col = divmod(cell, COLS)
        x0, y0 = (col * 16 + PIN_INSET) * scale, (row * 16 + PIN_INSET) * scale
        shape = [(x0 + x * scale, y0 + y * scale) for x, y in outline]
        draws["shadow"].polygon([(x, y + scale) for x, y in shape], fill=255)
        draws["fill"].polygon(shape, fill=255)
        draws["rim"].line(shape + [shape[0]], fill=255, width=round(scale), joint="curve")

    def layer(mask, rgba):
        r, g, b, a = rgba
        solid = Image.new("RGBA", size, (r, g, b, 0))
        solid.putalpha(mask.point(lambda v: round(v * a)))
        return solid

    pins = layer(masks["shadow"], PIN_SHADOW)
    pins = Image.alpha_composite(pins, layer(masks["fill"], PIN_FILL))
    pins = Image.alpha_composite(pins, layer(masks["rim"], PIN_RIM))
    # Reduce with premultiplied alpha, so the transparent white around each pin does not bleed into its edge.
    return pins.convert("RGBa").resize((COLS * CELL, ROWS * CELL), Image.LANCZOS).convert("RGBA")


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
    # A separate stream, so the panels never change the sprite or the no-location cells.
    offsets = place_panels(grid, mulberry32(SEED + 1))

    SPRITE.parent.mkdir(parents=True, exist_ok=True)
    sprite.save(SPRITE, "WEBP", quality=QUALITY, method=6)
    size = SPRITE.stat().st_size
    if size > MAX_BYTES:
        raise SystemExit(f"{SPRITE.name} is {size} bytes, over the {MAX_BYTES} budget")

    pins = build_pins(no_location)
    pins.save(PINS, "WEBP", lossless=True, method=6)
    pins_size = PINS.stat().st_size
    if pins_size > PINS_MAX_BYTES:
        raise SystemExit(f"{PINS.name} is {pins_size} bytes, over the {PINS_MAX_BYTES} budget")

    manifest = {
        "cols": COLS,
        "rows": ROWS,
        "cell": CELL,
        "seed": SEED,
        "sources": [name for name, _ in grid],
        "crops": [crop_index for _, crop_index in grid],
        "noLocation": no_location,
        "panels": {
            "cols": PANEL_COLS,
            "rows": PANEL_ROWS,
            "across": PANELS_ACROSS,
            "down": PANELS_DOWN,
            "offsets": [list(offset) for offset in offsets],
        },
    }
    MANIFEST.write_text(json.dumps(manifest, separators=(",", ":")) + "\n", encoding="utf8")
    print(f"{SPRITE.relative_to(ROOT)}: {size} bytes, {len(files)} photos, {len(no_location)} no-location cells")
    print(f"{PINS.relative_to(ROOT)}: {pins_size} bytes")


if __name__ == "__main__":
    main()
