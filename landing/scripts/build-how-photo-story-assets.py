"""Builds the #how photo story's textures and placeholders (python3 scripts/build-how-photo-story-assets.py, Pillow).

public/assets/how/heap.webp       a wide, dense pile of photo prints (two layers: dim back, bright front)
public/assets/how/gallery.webp    the same kind of prints scattered over a phone-shaped rectangle (the whole gallery)
src/data/how-photo-story-lqip.json    tiny blurred data-URI placeholders for the 경주 photos, heap and gallery
src/data/how-photo-story-sources.json the library photos each texture was made from (for the credits test)

The prints are tiny crops of the credited open-licence photos in public/assets/photos (every one except the 경주
photos, which the story shows on their own, and usa-05); they stand for "thousands of photos" and are never readable.
Same seeds, sizes and qualities as the approved sketch, so the output matches it byte for byte with the same Pillow.
"""
import base64
import glob
import io
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

LANDING = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PHOTOS = os.path.join(LANDING, 'public', 'assets', 'photos')
OUT = os.path.join(LANDING, 'public', 'assets', 'how')
DATA = os.path.join(LANDING, 'src', 'data')
STORY_PHOTOS = ['gyeongbuk-01', 'gyeongbuk-02', 'gyeongbuk-03', 'gyeongbuk-04']

srcs = sorted(f for f in glob.glob(PHOTOS + '/*.webp') if 'gyeongbuk' not in f and 'usa-05' not in f)
imgs = {f: Image.open(f).convert('RGB') for f in srcs}
drawn = set()   # the source files the current texture actually picked


def make_card(rng, base, bright, shade_a):
    """A white-bordered photo print, rotated, with its soft shadow. Returns (shadow, card) RGBA images, same size."""
    f = rng.choice(srcs)
    drawn.add(f)
    im = imgs[f]
    kind = rng.random()
    if kind < 0.66:
        w, h = base, base * 2 / 3
    elif kind < 0.88:
        w, h = base * 0.72, base * 0.92
    else:
        w, h = base * 0.8, base * 0.8
    w, h = max(8, int(w)), max(8, int(h))
    sw, sh = im.size
    tr = w / h
    if sw / sh > tr:
        cw = int(sh * tr); x0 = rng.randint(0, sw - cw); crop = im.crop((x0, 0, x0 + cw, sh))
    else:
        ch = int(sw / tr); y0 = rng.randint(0, sh - ch); crop = im.crop((0, y0, sw, y0 + ch))
    S = 2
    bw = max(3, round(base * 0.045))
    pw, ph = (w + 2 * bw) * S, (h + 2 * bw) * S
    card = Image.new('RGBA', (pw, ph), (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle([0, 0, pw - 1, ph - 1], radius=round(base * .075) * S, fill=(255, 255, 255, 255))
    photo = crop.resize((w * S, h * S), Image.LANCZOS)
    photo = ImageEnhance.Brightness(photo).enhance(bright)
    m = Image.new('L', photo.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, photo.size[0] - 1, photo.size[1] - 1], radius=round(base * .045) * S, fill=255)
    card.paste(photo, (bw * S, bw * S), m)
    if shade_a > 0:
        shade = Image.new('RGBA', card.size, (40, 60, 50, int(shade_a)))
        card = Image.composite(Image.alpha_composite(card, shade), card, card.split()[3])
    card = card.resize((pw // S, ph // S), Image.LANCZOS)
    rot = card.rotate(rng.uniform(-40, 40), resample=Image.BICUBIC, expand=True)
    pad = 8
    big = Image.new('RGBA', (rot.size[0] + pad * 2, rot.size[1] + pad * 2), (0, 0, 0, 0))
    big.paste(rot, (pad, pad))
    sh_a = big.split()[3].point(lambda a: int(a * 0.30))
    shadow = Image.new('RGBA', big.size, (20, 40, 30, 0))
    shadow.putalpha(sh_a)
    shadow = shadow.filter(ImageFilter.GaussianBlur(3))
    return shadow, big


def comp(canvas, im, px, py):
    """alpha_composite that tolerates negative offsets (clips)."""
    sx, sy = max(0, -px), max(0, -py)
    if sx >= im.size[0] or sy >= im.size[1]:
        return
    canvas.alpha_composite(im, (max(0, px), max(0, py)), (sx, sy))


def paste(canvas, rng, x, y, base, bright, shade_a):
    shadow, card = make_card(rng, base, bright, shade_a)
    px, py = int(x - card.size[0] / 2), int(y - card.size[1] / 2)
    comp(canvas, shadow, px + 2, py + 4)
    comp(canvas, card, px, py)


def used_files():
    files = sorted('/assets/photos/' + os.path.basename(f) for f in drawn)
    drawn.clear()
    return files


os.makedirs(OUT, exist_ok=True)
sources = {}

# ---------- heap: a wide, dense mound on a transparent canvas ----------
W, H = 1500, 900
PADC = 120
rng = random.Random(2026)
canvas = Image.new('RGBA', (W + 2 * PADC, H + 2 * PADC), (0, 0, 0, 0))
cx, cy = W / 2 + PADC, H / 2 + 20 + PADC
AX, AY = W / 2 - 50, H / 2 - 70


def rim(th):
    return 1 + 0.07 * math.sin(3 * th + 1.3) + 0.04 * math.sin(5 * th + 0.4) + 0.03 * math.sin(9 * th + 2.0)


def layer(n, rmax, umin, base_lo, base_hi, b_lo, b_hi, shade):
    items = []
    for _ in range(n):
        th = rng.uniform(0, math.tau)
        rr = rng.random() ** 0.5 * rmax
        items.append((rr, th))
    items.sort(key=lambda t: -t[0] + rng.uniform(-0.1, 0.1))
    for k, (rr, th) in enumerate(items):
        depth = k / n
        x = cx + math.cos(th) * rr * AX * rim(th)
        y = cy + math.sin(th) * rr * AY * rim(th) * (0.92 if math.sin(th) < 0 else 1.0)
        base = rng.uniform(base_lo, base_hi) * (1.0 - 0.10 * rr)
        paste(canvas, rng, x, y, base, b_lo + (b_hi - b_lo) * depth, shade * (1 - depth))


layer(1100, 1.0, 0, 56, 80, 0.70, 0.86, 70)     # back: many small, in shade
layer(700, 0.9, 0, 74, 104, 0.84, 1.03, 40)     # front: bigger, brighter, mounding to the centre
canvas = canvas.crop((PADC, PADC, PADC + W, PADC + H))
canvas.resize((1350, 810), Image.LANCZOS).save(OUT + '/heap.webp', 'WEBP', quality=60, method=6)
sources['heap'] = used_files()
print('heap', os.path.getsize(OUT + '/heap.webp'), 'bytes from', len(sources['heap']), 'photos')

# ---------- gallery: the whole library, scattered, filling a phone-shaped rectangle (never a grid) ----------
GW, GH = 560, 1120
rng = random.Random(14000)
g = Image.new('RGBA', (GW + 160, GH + 160), (233, 239, 235, 255))
for k in range(520):
    x = rng.uniform(0, GW + 160)
    y = rng.uniform(0, GH + 160)
    paste(g, rng, x, y, rng.uniform(70, 104), rng.uniform(0.86, 1.02), 0)
g = g.crop((80, 80, 80 + GW, 80 + GH)).convert('RGB')
g = g.resize((360, 720), Image.LANCZOS)   # shown at 16-50% opacity under the cards: 360x720 q50 is plenty
g.save(OUT + '/gallery.webp', 'WEBP', quality=50, method=6)
sources['gallery'] = used_files()
print('gallery', os.path.getsize(OUT + '/gallery.webp'), 'bytes from', len(sources['gallery']), 'photos')


# ---------- LQIP: tiny blurred placeholders, so a slow network shows soft photos-to-come, not empty frames ----------
def uri(im, q):
    b = io.BytesIO()
    im.save(b, 'WEBP', quality=q, method=6)
    return 'data:image/webp;base64,' + base64.b64encode(b.getvalue()).decode()


lqip = {}
for n in STORY_PHOTOS:
    im = Image.open(f'{PHOTOS}/{n}.webp').convert('RGB')
    w = 24
    h = max(1, round(im.height * w / im.width))
    lqip[n] = uri(im.resize((w, h), Image.LANCZOS), 50)
heap = Image.open(OUT + '/heap.webp').convert('RGBA')
lqip['heap'] = uri(heap.resize((48, 29), Image.LANCZOS), 45)
gal = Image.open(OUT + '/gallery.webp').convert('RGB')
lqip['gallery'] = uri(gal.resize((12, 24), Image.LANCZOS), 45)
with open(DATA + '/how-photo-story-lqip.json', 'w', encoding='utf-8') as f:
    f.write(json.dumps(lqip, separators=(',', ':')) + '\n')
print('lqip', os.path.getsize(DATA + '/how-photo-story-lqip.json'), 'bytes')

with open(DATA + '/how-photo-story-sources.json', 'w', encoding='utf-8') as f:
    f.write(json.dumps({'note': 'Library photos baked into public/assets/how/heap.webp and gallery.webp '
                                '(scripts/build-how-photo-story-assets.py). Each must keep its credit.',
                        **sources}, indent=2) + '\n')
