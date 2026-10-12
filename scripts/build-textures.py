#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Builds the Void Siphon's item sprite from Malcolm Riley's unused textures in data/art/ (NOTICE).

Its recipe drawn as one wand: the infernal rod's lower end stands for the blaze rod, the obsidian
rod's upper end for the crying obsidian, and the bound crystal, shrunk onto the tip, for the ghast
tear. Run from the repository root; it needs Pillow.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "data" / "art"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "voidworks" / "textures" / "item" / "void_siphon.png"

# Along the rods' diagonal, how far up from the lower-left corner the blaze handle reaches.
HANDLE = 14
# The crystal's size on the tip, and where its top-left corner sits.
TIP_SIZE = 7
TIP_AT = (9, 0)


def sprite(name):
    image = Image.open(ART / f"{name}.png").convert("RGBA")
    # An animated sprite's frames stack downwards: keep the first.
    return image.crop((0, 0, image.width, image.width))


def main():
    handle, shaft, crystal = sprite("part_rod_infernal"), sprite("part_rod_obsidian"), sprite("artifact_bound_crystal")
    siphon = Image.new("RGBA", (16, 16))
    pixels, lower, upper = siphon.load(), handle.load(), shaft.load()
    for y in range(16):
        for x in range(16):
            pixels[x, y] = lower[x, y] if x + (15 - y) < HANDLE else upper[x, y]
    siphon.alpha_composite(crystal.resize((TIP_SIZE, TIP_SIZE), Image.NEAREST), TIP_AT)
    siphon.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
