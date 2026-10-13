# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Temporary cover and icon, in the suite's style (Fieldworks' and Pipeworks' make-cover.py), until an
# in-game screenshot or an artist's art replaces them. Run from the repo root with Pillow installed.
import os
import random

from PIL import Image, ImageDraw, ImageFont

T = 'src/main/resources/assets/voidworks/textures/'
BG = (18, 10, 24)
ACCENT = (196, 54, 247)  # GradeColor's grade 14
FADE = 60  # 0 leaves the Voidstone at full strength, 255 hides it
# the End, the Overworld and the Nether, left to right, as void runs downhill
KINDS = ['end_voidstone', 'voidstone', 'nether_voidstone']
DISPLACERS = ['end_void_displacer_front', 'overworld_void_displacer_side', 'nether_void_displacer_side']
GRADES = [0xF75036, 0xC77C2C, 0xF7E436, 0x9BC72C, 0x77F736, 0x2CC734, 0x36F78A, 0x2CC7AA,
          0x36D1F7, 0x2C6CC7, 0x363DF7, 0x622CC7, 0xC436F7, 0xC72CB5, 0xF73697]


def sprite(name, px):
    # an animated sprite's first frame
    im = Image.open(f'{T}block/{name}.png').convert('RGBA')
    return im.crop((0, 0, im.width, im.width)).resize((px, px), Image.NEAREST)


def butterfly(rgb, px):
    im = Image.open(f'{T}particle/mote_butterfly_0.png').convert('LA')
    out = Image.new('RGBA', im.size)
    for xy in ((x, y) for x in range(im.width) for y in range(im.height)):
        l, a = im.getpixel(xy)
        out.putpixel(xy, tuple(c * l // 255 for c in rgb) + (a,))
    return out.resize((px, px), Image.NEAREST)


def field(W, H, px, seed):
    # Voidstone in each dimension's colours, a Displacer in each, motes flying off as butterflies
    rng = random.Random(seed)
    img = Image.new('RGBA', (W, H))
    cols, rows = W // px + 1, H // px + 1
    for cy in range(rows):
        for cx in range(cols):
            k = min(2, max(0, int(3 * (cx + rng.uniform(-1.5, 1.5)) / cols)))
            img.paste(sprite(KINDS[k], px), (cx * px, cy * px))
    for k, name in enumerate(DISPLACERS):
        img.paste(sprite(name, px), (int((k + 0.5) * cols / 3) * px, int(rows * (0.2 + 0.3 * k)) * px))
    for _ in range(W * H // (px * px * 6)):
        g = GRADES[rng.randrange(len(GRADES))]
        b = butterfly(((g >> 16) & 255, (g >> 8) & 255, g & 255), int(px * rng.uniform(0.5, 0.9)))
        img.alpha_composite(b, (rng.randrange(W - b.width), rng.randrange(H - b.height)))
    return img


def font(names, size):
    for n in names:
        if os.path.exists(n):
            return ImageFont.truetype(n, size)
    raise SystemExit('needs one of ' + ', '.join(names))


def make(W, H, out, title_size, px):
    img = field(W, H, px, 5)
    img = Image.alpha_composite(img, Image.new('RGBA', (W, H), BG + (FADE,)))
    cy, bh = H // 2, int(title_size * 1.6)
    band = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(band).rectangle([0, cy - bh // 2, W, cy + bh // 2], fill=(12, 8, 16, 225))
    img = Image.alpha_composite(img, band)
    d = ImageDraw.Draw(img)
    lt = max(3, title_size // 25)
    d.rectangle([0, cy - bh // 2, W, cy - bh // 2 + lt], fill=ACCENT)
    d.rectangle([0, cy + bh // 2 - lt, W, cy + bh // 2], fill=ACCENT)
    f1 = font(['/System/Library/Fonts/Supplemental/Impact.ttf', '/usr/share/fonts/TTF/impact.ttf',
               os.path.expanduser('~/.local/share/fonts/msfonts/impact.ttf')], title_size)
    f2 = font(['/System/Library/Fonts/Supplemental/Arial Black.ttf', '/usr/share/fonts/TTF/ariblk.ttf',
               os.path.expanduser('~/.local/share/fonts/msfonts/ariblk.ttf')], int(title_size * 0.62))
    a, w = 'VOID', 'works'
    b1, b2 = d.textbbox((0, 0), a, font=f1), d.textbbox((0, 0), w, font=f2)
    w1, w2, h1, h2 = b1[2] - b1[0], b2[2] - b2[0], b1[3] - b1[1], b2[3] - b2[1]
    pad, gap, sh = int(title_size * 0.16), int(title_size * 0.1), max(3, title_size // 20)
    bw = w2 + 2 * pad
    x = (W - (w1 + gap + bw)) // 2
    y = cy - h1 // 2 - b1[1]
    d.text((x + sh - b1[0], y + sh), a, font=f1, fill=(0, 0, 0))
    d.text((x - b1[0], y), a, font=f1, fill=ACCENT)
    bx, bt = x + w1 + gap, cy - h1 // 2
    d.rounded_rectangle([bx + sh, bt + sh, bx + bw + sh, bt + h1 + sh], radius=pad, fill=(0, 0, 0))
    d.rounded_rectangle([bx, bt, bx + bw, bt + h1], radius=pad, fill=(240, 240, 236))
    d.text((bx + pad - b2[0], bt + (h1 - h2) // 2 - b2[1]), w, font=f2, fill=BG)
    img.convert('RGB').save(out)


make(1280, 640, 'publish/voidworks-cover.png', 150, 64)
make(512, 512, 'publish/voidworks-icon.png', 80, 48)
