# The Matter Link: two of the runner's Void Wells 12 blocks apart, on networks of their own, each with
# a Well hatch on its south wall beside its core. The west Well's provider holds four stacks of gold
# blocks, the east Well's requester asks for three, and a grown Matter-line dragon resting at the
# west Well carries them across, diving into one shaft and emerging from the other (ADR 0011).
#
# The plot runs from ~2 to ~25 east and from ~-12 to the sign at ~0 south, 16 high, cleared as
# every scene's is, its dragons with it. Its hatches are broken first: a fill takes a block away
# without its side effects, so a hatch filled away would keep its record, and readying it again would
# drop what it held onto the new plot. Broken, they drop it now, for the clear to take away.
setblock ~9 ~2 ~-3 minecraft:air destroy
setblock ~21 ~2 ~-3 minecraft:air destroy
function voidworks:gallery/clear

# The providing Well, its core in its south wall facing the sign, its provider east of the core.
fill ~6 ~ ~-8 ~10 ~3 ~-4 voidworks:void_well_casing
fill ~7 ~1 ~-7 ~9 ~3 ~-5 minecraft:air
fill ~6 ~4 ~-8 ~10 ~4 ~-4 voidworks:void_well_rim
fill ~7 ~4 ~-7 ~9 ~4 ~-5 minecraft:air
setblock ~8 ~2 ~-4 voidworks:void_well[facing=south]
voidworks well ~8 ~2 ~-4 none
setblock ~9 ~2 ~-3 voidworks:well_hatch[facing=south,mode=provider]
voidworks hatch ~9 ~2 ~-3 provider minecraft:gold_block 4

# The requesting Well, 12 blocks east, its requester east of its core.
fill ~18 ~ ~-8 ~22 ~3 ~-4 voidworks:void_well_casing
fill ~19 ~1 ~-7 ~21 ~3 ~-5 minecraft:air
fill ~18 ~4 ~-8 ~22 ~4 ~-4 voidworks:void_well_rim
fill ~19 ~4 ~-7 ~21 ~4 ~-5 minecraft:air
setblock ~20 ~2 ~-4 voidworks:void_well[facing=south]
voidworks well ~20 ~2 ~-4 none
setblock ~21 ~2 ~-3 voidworks:well_hatch[facing=south,mode=requester]
voidworks hatch ~21 ~2 ~-3 requester minecraft:gold_block 3

voidworks dragon ~8 ~2 ~-4 matter
function voidworks:gallery/sign {scene:"matter_link"}
