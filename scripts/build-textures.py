#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Builds sprites from Malcolm Riley's unused textures in data/art/ (NOTICE).

The Void Siphon's item sprite is its recipe drawn as one wand: the infernal rod's lower end stands
for the blaze rod, the obsidian rod's upper end for the crying obsidian, and the bound crystal,
shrunk onto the tip, for the ghast tear.

The Overworld Void Displacer's faces are the abyssium block, a casing in crying obsidian's purple,
with the lit brooding forge's void flame let into each side and its glowing eye into the top. Every
frame of the forge's animation is built, and its animation copied beside the sprite.

The End Void Displacer's faces are built the same way on the abyssium recoloured into end stone's
pale yellows, so the family reads as one in the End's own colours: lit while it works, and the
unlit brooding forge's faces, a single frame, while it is Dormant.

Voidstone is the obsidian sand recoloured into the abyssium's dark purples, so the Displacer's body
reads as its family while the brighter, framed Displacer stands out from it.

Run from the repository root; it needs Pillow.
"""

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "data" / "art"
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "voidworks" / "textures"

# Along the rods' diagonal, how far up from the lower-left corner the blaze handle reaches.
HANDLE = 14
# The crystal's size on the tip, and where its top-left corner sits.
TIP_SIZE = 7
TIP_AT = (9, 0)

# The boxes, (left, top, right, bottom), of the forge's flame on its side and of its eye on its
# top, let into the abyssium casing.
FLAME = (4, 3, 12, 13)
EYE = (5, 5, 11, 11)

# The End casing's shades, darkest to lightest, which the abyssium's run through by brightness: end
# stone's pale yellows, darkened at the bottom so the casing keeps its depth.
END_CASING = [(0x3A, 0x38, 0x24), (0xAE, 0xAC, 0x76), (0xEC, 0xEE, 0xB6)]

# The abyssium's darkest and middling purples, which Voidstone's darkest and lightest pixels become.
VOIDSTONE_DARK = (0x0F, 0x00, 0x1D)
VOIDSTONE_LIGHT = (0x62, 0x12, 0x50)


def frames(name):
    image = Image.open(ART / f"{name}.png").convert("RGBA")
    # An animated sprite's frames stack downwards.
    size = image.width
    return [image.crop((0, y, size, y + size)) for y in range(0, image.height, size)]


def sprite(name):
    return frames(name)[0]


def stack(images):
    strip = Image.new("RGBA", (16, 16 * len(images)))
    for i, image in enumerate(images):
        strip.paste(image, (0, 16 * i))
    return strip


def save(image, path):
    image.save(path)
    print(f"wrote {path.relative_to(ROOT)}")


def void_siphon():
    handle, shaft, crystal = sprite("part_rod_infernal"), sprite("part_rod_obsidian"), sprite("artifact_bound_crystal")
    siphon = Image.new("RGBA", (16, 16))
    pixels, lower, upper = siphon.load(), handle.load(), shaft.load()
    for y in range(16):
        for x in range(16):
            pixels[x, y] = lower[x, y] if x + (15 - y) < HANDLE else upper[x, y]
    siphon.alpha_composite(crystal.resize((TIP_SIZE, TIP_SIZE), Image.NEAREST), TIP_AT)
    save(siphon, TEXTURES / "item" / "void_siphon.png")


def recolour(image, shades):
    """Runs each pixel's brightness, from the image's darkest to its lightest, through shades."""
    image = image.copy()
    pixels = image.load()
    brightness = [sum(pixels[x, y][:3]) for y in range(image.height) for x in range(image.width)]
    darkest, lightest = min(brightness), max(brightness)
    for y in range(image.height):
        for x in range(image.width):
            *_, alpha = pixels[x, y]
            t = (sum(pixels[x, y][:3]) - darkest) / (lightest - darkest) * (len(shades) - 1)
            low = min(int(t), len(shades) - 2)
            d, l = shades[low], shades[low + 1]
            pixels[x, y] = (*(round(a + (b - a) * (t - low)) for a, b in zip(d, l)), alpha)
    return image


def displacer_face(casing, forge, box, name):
    built = []
    for frame in frames(forge):
        image = casing.copy()
        image.paste(frame.crop(box), box[:2])
        built.append(image)
    path = TEXTURES / "block" / f"{name}.png"
    save(stack(built), path)
    mcmeta = ART / f"{forge}.png.mcmeta"
    if mcmeta.exists():
        shutil.copyfile(mcmeta, f"{path}.mcmeta")


def voidstone():
    sand = sprite("sand_obsidian")
    pixels = sand.load()
    shades = [sum(pixels[x, y][:3]) for y in range(16) for x in range(16)]
    darkest, lightest = min(shades), max(shades)
    for y in range(16):
        for x in range(16):
            *_, alpha = pixels[x, y]
            t = (sum(pixels[x, y][:3]) - darkest) / (lightest - darkest)
            tint = tuple(round(d + (l - d) * t) for d, l in zip(VOIDSTONE_DARK, VOIDSTONE_LIGHT))
            pixels[x, y] = (*tint, alpha)
    save(sand, TEXTURES / "block" / "voidstone.png")


def main():
    void_siphon()
    abyssium = sprite("block_metal_abyssium")
    displacer_face(abyssium, "device_brooding_forge_side_on", FLAME, "overworld_void_displacer_side")
    displacer_face(abyssium, "device_brooding_forge_up_on", EYE, "overworld_void_displacer_top")
    end_casing = recolour(abyssium, END_CASING)
    displacer_face(end_casing, "device_brooding_forge_side_on", FLAME, "end_void_displacer_side")
    displacer_face(end_casing, "device_brooding_forge_up_on", EYE, "end_void_displacer_top")
    displacer_face(end_casing, "device_brooding_forge_side_off", FLAME, "end_void_displacer_side_dormant")
    displacer_face(end_casing, "device_brooding_forge_up_off", EYE, "end_void_displacer_top_dormant")
    voidstone()


if __name__ == "__main__":
    main()
