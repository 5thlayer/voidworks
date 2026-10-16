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

The Void Well is masonry in the placeholder's colours, so it reads as a built well and not a box of
panels: its casing the large brooding brick in obsidian's shades, its rim the small stone tiles in
purpur's, both tiling without a seam across the well's faces. Its core's front is a window in the
casing, framed in the rim's tiles, with the roiling plasma churning behind it in the void's purples,
every frame of it and its animation copied beside the sprite; Dormant, the window shows still dark.

The Drained Dragon Egg's shell is the bonetree's bark recoloured into a washed-out end stone white,
pale bone drained of void, shaded darker toward its foot. It is drawn as the vanilla egg's sprite is,
the egg seen from the front, so it fits the egg's shape. Only an egg, its first crack shows dark;
soaking, its cracks spread at each of the four stages and glow the brighter in amethyst, each stage's
cracks a sprite of their own laid over the shell.

The void's 12 stage masks, which a block it takes is eaten away through, are placeholders until they
are drawn (#107): the void spreads from a few spots, each pixel reached as soon as the nearest spot,
its distance roughened by noise, comes to it, the same share of the face more at every stage. A mask
paints, in the void's darkest purple, every pixel reached by its stage, so each covers the last.

Run from the repository root; it needs Pillow.
"""

import random
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

# Obsidian's and purpur's shades, darkest to lightest, which the Void Well's casing and rim run
# through: the placeholder's colours, read off vanilla's obsidian and purpur pillar.
WELL_CASING = [(0x00, 0x00, 0x01), (0x10, 0x0C, 0x1C), (0x3B, 0x27, 0x54)]
WELL_RIM = [(0x90, 0x65, 0x90), (0xAC, 0x7B, 0xAC), (0xC7, 0xA8, 0xC7)]
# The void behind the core's window, darkest to lightest, and the box of the window with its frame.
WELL_VOID = [(0x0A, 0x02, 0x14), (0x3C, 0x0C, 0x6E), (0x9A, 0x4C, 0xE6), (0xE6, 0xC0, 0xFF)]
WINDOW = (2, 2, 14, 14)

# The Drained Dragon Egg's shell, darkest to lightest: end stone's yellows washed out to bone, and
# how much darker its foot is than its crown.
EGG_SHELL = [(0x7E, 0x7A, 0x6C), (0xCF, 0xCB, 0xB6), (0xF2, 0xF0, 0xE2)]
EGG_FOOT = 0.18
# The cracks' runs, each from the pixel before it, that each of the egg's four stages adds to the
# last's: the first runs down from its crown, and the rest spread from it toward its foot.
EGG_CRACKS = [
    [[(8, 1), (8, 3), (7, 4), (7, 6), (8, 7)]],
    [[(8, 7), (9, 8), (9, 10)], [(7, 5), (5, 6), (4, 7)]],
    [[(9, 10), (8, 12), (8, 13)], [(9, 8), (11, 9), (12, 11)], [(4, 7), (3, 9), (2, 10)]],
    [[(8, 13), (7, 15)], [(12, 11), (13, 12)], [(8, 3), (10, 4), (11, 6)], [(2, 10), (2, 12)]],
]
# The first crack's shade on an egg that is not soaking, and the cracks' glow at each stage.
EGG_CRACK_DARK = (0x3E, 0x38, 0x30)
EGG_GLOW = [(0x7A, 0x3C, 0xB0), (0xA0, 0x5A, 0xE0), (0xC8, 0x8C, 0xFF), (0xE8, 0xB0, 0xFF)]

# The void's stage masks: how many stages, the spots it spreads from on a face, how rough its edge is,
# in pixels, and the seed that fixes both, so the placeholders come out the same on every run.
TAKING_STAGES = 12
TAKING_SPOTS = 3
TAKING_ROUGHNESS = 3.0
TAKING_SEED = 98

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


def well_window(casing, rim, inside):
    """The casing with the window let into it: the rim's tiles for its frame, and inside it, inside."""
    face = casing.copy()
    pixels, frame, view = face.load(), rim.load(), inside.load()
    left, top, right, bottom = WINDOW
    for y in range(top, bottom):
        for x in range(left, right):
            edge = x in (left, right - 1) or y in (top, bottom - 1)
            pixels[x, y] = frame[x, y] if edge else view[x, y]
    return face


def void_well():
    casing = recolour(sprite("brooding_brick_large"), WELL_CASING)
    rim = recolour(sprite("stone_tiles_small"), WELL_RIM)
    save(casing, TEXTURES / "block" / "void_well_casing.png")
    save(rim, TEXTURES / "block" / "void_well_rim.png")
    front = TEXTURES / "block" / "void_well_front.png"
    save(stack([well_window(casing, rim, recolour(frame, WELL_VOID)) for frame in frames("fluid_roiling_plasma")]), front)
    shutil.copyfile(ART / "fluid_roiling_plasma.png.mcmeta", f"{front}.mcmeta")
    still = Image.new("RGBA", (16, 16), (*WELL_VOID[0], 255))
    save(well_window(casing, rim, still), TEXTURES / "block" / "void_well_front_dormant.png")


def crack_pixels(stage):
    """Every pixel of the egg's cracks up to stage, each run stepped one pixel at a time."""
    pixels = []
    for runs in EGG_CRACKS[: stage + 1]:
        for run in runs:
            for (x0, y0), (x1, y1) in zip(run, run[1:]):
                steps = max(abs(x1 - x0), abs(y1 - y0))
                pixels += [(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps)) for i in range(steps + 1)]
    return pixels


def drained_dragon_egg():
    shell = recolour(sprite("log_bonetree_sides"), EGG_SHELL)
    pixels = shell.load()
    for y in range(16):
        for x in range(16):
            *colour, alpha = pixels[x, y]
            pixels[x, y] = (*(round(c * (1 - EGG_FOOT * y / 15)) for c in colour), alpha)
    for x, y in crack_pixels(0):
        pixels[x, y] = (*EGG_CRACK_DARK, 255)
    save(shell, TEXTURES / "block" / "drained_dragon_egg.png")
    for stage, glow in enumerate(EGG_GLOW):
        cracks = Image.new("RGBA", (16, 16))
        for x, y in crack_pixels(stage):
            cracks.putpixel((x, y), (*glow, 255))
        save(cracks, TEXTURES / "block" / f"drained_dragon_egg_cracks_{stage}.png")


def taking_masks():
    rng = random.Random(TAKING_SEED)
    spots = [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(TAKING_SPOTS)]
    # Value noise on a coarse grid, read smoothly between its points, so the edge is ragged, not round.
    grid = [[rng.uniform(0, TAKING_ROUGHNESS) for _ in range(6)] for _ in range(6)]

    def noise(x, y):
        gx, gy = x / 3.2, y / 3.2
        x0, y0 = int(gx), int(gy)
        fx, fy = gx - x0, gy - y0
        top = grid[y0][x0] * (1 - fx) + grid[y0][x0 + 1] * fx
        bottom = grid[y0 + 1][x0] * (1 - fx) + grid[y0 + 1][x0 + 1] * fx
        return top * (1 - fy) + bottom * fy

    def reached(pixel):
        x, y = pixel[0] + 0.5, pixel[1] + 0.5
        return min(((x - sx) ** 2 + (y - sy) ** 2) ** 0.5 for sx, sy in spots) + noise(x, y) + rng.uniform(0, 0.5)

    order = sorted(((x, y) for y in range(16) for x in range(16)), key=reached)
    for stage in range(1, TAKING_STAGES + 1):
        mask = Image.new("RGBA", (16, 16))
        for pixel in order[: round(len(order) * stage / TAKING_STAGES)]:
            mask.putpixel(pixel, (*VOIDSTONE_DARK, 255))
        save(mask, TEXTURES / "void_taking" / f"stage_{stage}.png")


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
    void_well()
    drained_dragon_egg()
    taking_masks()


if __name__ == "__main__":
    main()
