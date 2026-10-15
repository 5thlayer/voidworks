# The Void Dragon: two of the runner's Void Wells 12 blocks apart, on networks of their own, a
# reservoir to the west filled with motes worth something wherever it is built, one to the east
# whose target is full, and a grown dragon resting at the reservoir that carries between them,
# diving into one shaft and emerging from the other.
#
# The plot runs from ~2 to ~25 east and from ~-12 to the sign at ~0 south, 16 high on a stone floor.
# The dragons resting there or on a trip to or from it go first, so building it again leaves one.
voidworks dragon remove ~2 ~-1 ~-12 ~25 ~15 ~
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air

# The reservoir, its core in its south wall facing the sign, a Voidstone pillar against its west wall.
fill ~6 ~ ~-8 ~10 ~3 ~-4 voidworks:void_well_casing
fill ~7 ~1 ~-7 ~9 ~3 ~-5 minecraft:air
fill ~6 ~4 ~-8 ~10 ~4 ~-4 voidworks:void_well_rim
fill ~7 ~4 ~-7 ~9 ~4 ~-5 minecraft:air
setblock ~8 ~2 ~-4 voidworks:void_well[facing=south]
fill ~5 ~ ~-6 ~5 ~1 ~-6 voidworks:voidstone
voidworks well ~8 ~2 ~-4 none
# One grade above the End's Void Pressure over the open void, the highest a place has as shipped.
voidworks mote ~5 ~1 ~-6 5

# The Well short of its target, empty, 12 blocks east.
fill ~18 ~ ~-8 ~22 ~3 ~-4 voidworks:void_well_casing
fill ~19 ~1 ~-7 ~21 ~3 ~-5 minecraft:air
fill ~18 ~4 ~-8 ~22 ~4 ~-4 voidworks:void_well_rim
fill ~19 ~4 ~-7 ~21 ~4 ~-5 minecraft:air
setblock ~20 ~2 ~-4 voidworks:void_well[facing=south]
voidworks well ~20 ~2 ~-4 full

voidworks dragon ~8 ~2 ~-4
setblock ~14 ~ ~ minecraft:oak_sign[rotation=0]{front_text:{messages:[{translate:"voidworks.gallery.void_dragon",bold:true},{translate:"voidworks.gallery.void_dragon.1"},{translate:"voidworks.gallery.void_dragon.2"},{translate:"voidworks.gallery.void_dragon.3"}]}}
