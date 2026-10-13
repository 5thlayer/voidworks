#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Builds sprites from Malcolm Riley's unused textures in data/art/ (NOTICE).

The Void Siphon's item sprite is its recipe drawn as one wand: the infernal rod's lower end stands
for the blaze rod, the obsidian rod's upper end for the crying obsidian, and the bound crystal,
shrunk onto the tip, for the ghast tear. Its higher tiers are the same wand with the crystal
recoloured into what climbs it: the Resonant Siphon's into an echo shard's deep teals, the Draconic's
into dragon's breath's pinks.

The Overworld Void Displacer's faces are the abyssium block, a casing in crying obsidian's purple,
with the lit brooding forge's void flame let into each side and its glowing eye into the top. Every
frame of the forge's animation is built, and its animation copied beside the sprite.

The End Void Displacer's faces are built the same way on the abyssium recoloured into end stone's
pale yellows, so the family reads as one in the End's own colours: lit while it works, and the
unlit brooding forge's flame, a single frame, while it is Dormant. The flame is only on its front,
the face it grows through, so the way it faces shows; its other five faces are the bare casing.

The Nether Void Displacer's faces are the Overworld's, flame on each side and eye on the top, on
the abyssium recoloured into nether bricks' dark reds: lit while it works, and the unlit brooding
forge's flame and eye, a single frame each, while it is Dormant.

Voidstone is the obsidian sand recoloured into the abyssium's dark purples, so the Displacer's body
reads as its family while the brighter, framed Displacer stands out from it. In the End it is the
same sand in the End casing's yellows, and in the Nether in the Nether casing's reds, so each
dimension's Displacer reads as part of its body there.

The Unmaking Chamber's sides are the gearbox clutch, its wheel standing for the grindstone,
recoloured into the abyssium's purples and let into the abyssium casing inside its frame, so it
reads as a void machine beside the Displacers. Awake, the clutch's four engaged lights, red in the
lit clutch, glow amethyst; Dormant, they stay the casing's own.

The Mutation Chamber's sides are the workbench's crafting grid, standing for the smithing table,
recoloured into the abyssium's purples and let into the casing inside its frame as the Unmaking
Chamber's clutch is. Awake, the grid's cells glow amethyst; Dormant, they stay the casing's own.

The Void Crucible's sides are the urn's base, a plain framed panel, let into the casing the same
way, so it reads as the quiet one beside the Chambers' lit faces. Its top is the casing ringed by
the crystal glass's rim, which frames the result floating above it: the rim in the casing's own
purples while it is idle, and amethyst while it upgrades.

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

# The crystal's shades on the higher Siphon tiers, darkest to lightest: an echo shard's teals and
# dragon's breath's pinks.
RESONANT_CRYSTAL = [(0x05, 0x1E, 0x26), (0x0B, 0x6B, 0x78), (0x6F, 0xE6, 0xE0)]
DRACONIC_CRYSTAL = [(0x3A, 0x06, 0x30), (0xB0, 0x2E, 0x8E), (0xFF, 0xB8, 0xF0)]

# The boxes, (left, top, right, bottom), of the forge's flame on its side and of its eye on its
# top, let into the abyssium casing.
FLAME = (4, 3, 12, 13)
EYE = (5, 5, 11, 11)

# The End casing's shades, darkest to lightest, which the abyssium's run through by brightness: end
# stone's pale yellows, darkened at the bottom so the casing keeps its depth.
END_CASING = [(0x3A, 0x38, 0x24), (0xAE, 0xAC, 0x76), (0xEC, 0xEE, 0xB6)]

# The Nether casing's shades, darkest to lightest: nether bricks' dark reds, lifted at the top so
# the casing's rivets still catch the light.
NETHER_CASING = [(0x16, 0x06, 0x08), (0x5A, 0x1A, 0x1E), (0x9C, 0x4A, 0x44)]

# The abyssium's darkest and middling purples, which Voidstone's darkest and lightest pixels become.
VOIDSTONE_DARK = (0x0F, 0x00, 0x1D)
VOIDSTONE_LIGHT = (0x62, 0x12, 0x50)

# The box of a sprite let into the casing, inside the casing's frame.
CLUTCH = (2, 2, 14, 14)
# The box of the crafting grid's cells, which glow on the awake Mutation Chamber, and how bright a
# cell's pixels are in the workbench, from darkest to lightest.
GRID = (4, 4, 12, 12)
CELL = (380, 600)
# What the lit clutch's engaged lights become on the awake Unmaking Chamber.
AMETHYST = (0xD6, 0x8C, 0xFF)


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


def void_siphon(name="void_siphon", crystal_shades=None):
    handle, shaft, crystal = sprite("part_rod_infernal"), sprite("part_rod_obsidian"), sprite("artifact_bound_crystal")
    if crystal_shades:
        crystal = recolour(crystal, crystal_shades)
    siphon = Image.new("RGBA", (16, 16))
    pixels, lower, upper = siphon.load(), handle.load(), shaft.load()
    for y in range(16):
        for x in range(16):
            pixels[x, y] = lower[x, y] if x + (15 - y) < HANDLE else upper[x, y]
    siphon.alpha_composite(crystal.resize((TIP_SIZE, TIP_SIZE), Image.NEAREST), TIP_AT)
    save(siphon, TEXTURES / "item" / f"{name}.png")


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
    save(recolour(sand, [VOIDSTONE_DARK, VOIDSTONE_LIGHT]), TEXTURES / "block" / "voidstone.png")
    save(recolour(sand, END_CASING), TEXTURES / "block" / "end_voidstone.png")
    save(recolour(sand, NETHER_CASING), TEXTURES / "block" / "nether_voidstone.png")


def let_into(abyssium, name):
    """The sprite recoloured into the abyssium's purples and let into the casing inside its frame."""
    shades = sorted({pixel[:3] for pixel in abyssium.get_flattened_data() if pixel[3]}, key=sum)
    face = abyssium.copy()
    face.paste(recolour(sprite(name), shades).crop(CLUTCH), CLUTCH[:2])
    return face


def mutation_chamber(abyssium):
    dormant = let_into(abyssium, "device_workbench_top")
    save(dormant, TEXTURES / "block" / "mutation_chamber_side_dormant.png")
    awake = dormant.copy()
    pixels, grid = awake.load(), sprite("device_workbench_top").load()
    for y in range(GRID[1], GRID[3]):
        for x in range(GRID[0], GRID[2]):
            if CELL[0] < sum(grid[x, y][:3]) < CELL[1]:
                pixels[x, y] = (*AMETHYST, 255)
    save(awake, TEXTURES / "block" / "mutation_chamber_side.png")


def void_crucible(abyssium):
    save(let_into(abyssium, "device_urn_bottom"), TEXTURES / "block" / "void_crucible_side.png")
    rim = sprite("glass_crystal_2")
    shades = sorted({pixel[:3] for pixel in abyssium.get_flattened_data() if pixel[3]}, key=sum)
    for shade, name in ((shades, "void_crucible_top"), ([(0, 0, 0), AMETHYST], "void_crucible_top_working")):
        top = abyssium.copy()
        top.alpha_composite(recolour(rim, shade))
        save(top, TEXTURES / "block" / f"{name}.png")


def unmaking_chamber(abyssium):
    dormant = let_into(abyssium, "device_gearbox_clutch_off")
    save(dormant, TEXTURES / "block" / "unmaking_chamber_side_dormant.png")
    awake = dormant.copy()
    pixels, lit = awake.load(), sprite("device_gearbox_clutch_on").load()
    for y in range(16):
        for x in range(16):
            red, green, _, alpha = lit[x, y]
            if alpha and red > 150 and green < 90:
                pixels[x, y] = (*AMETHYST, 255)
    save(awake, TEXTURES / "block" / "unmaking_chamber_side.png")


def main():
    void_siphon()
    void_siphon("resonant_void_siphon", RESONANT_CRYSTAL)
    void_siphon("draconic_void_siphon", DRACONIC_CRYSTAL)
    abyssium = sprite("block_metal_abyssium")
    displacer_face(abyssium, "device_brooding_forge_side_on", FLAME, "overworld_void_displacer_side")
    displacer_face(abyssium, "device_brooding_forge_up_on", EYE, "overworld_void_displacer_top")
    end_casing = recolour(abyssium, END_CASING)
    displacer_face(end_casing, "device_brooding_forge_side_on", FLAME, "end_void_displacer_front")
    displacer_face(end_casing, "device_brooding_forge_side_off", FLAME, "end_void_displacer_front_dormant")
    save(end_casing, TEXTURES / "block" / "end_void_displacer_casing.png")
    nether_casing = recolour(abyssium, NETHER_CASING)
    displacer_face(nether_casing, "device_brooding_forge_side_on", FLAME, "nether_void_displacer_side")
    displacer_face(nether_casing, "device_brooding_forge_up_on", EYE, "nether_void_displacer_top")
    displacer_face(nether_casing, "device_brooding_forge_side_off", FLAME, "nether_void_displacer_side_dormant")
    displacer_face(nether_casing, "device_brooding_forge_up_off", EYE, "nether_void_displacer_top_dormant")
    voidstone()
    mutation_chamber(abyssium)
    unmaking_chamber(abyssium)
    void_crucible(abyssium)


if __name__ == "__main__":
    main()
