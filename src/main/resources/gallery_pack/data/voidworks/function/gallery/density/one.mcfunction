# One Density's Displacer at ~x, its one stone beside it in plain sight, east, so its swap for
# Voidstone shows. Under both, a pad of six Voidstone the stone joins: room for 96 motes, more than a
# diamond block's 64, and apart from the next Density's pad.
$fill ~$(x) ~ ~-7 ~$(x) ~ ~-6 voidworks:voidstone
$execute positioned ~$(x) ~ ~-7 run fill ~1 ~ ~ ~2 ~ ~1 voidworks:voidstone
$setblock ~$(x) ~1 ~-6 voidworks:overworld_void_displacer
$execute positioned ~$(x) ~1 ~-6 run setblock ~1 ~ ~ minecraft:stone
$setblock ~$(x) ~2 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"$(block)",count:1}]}
# The block it swaps in, framed on its south face, toward the sign. A frame's NBT names its block
# absolutely, which a function cannot, but the summon puts it where it is summoned once it is loaded.
$summon minecraft:item_frame ~$(x) ~1 ~-5 {Facing:3b,Fixed:1b,Invulnerable:1b,Item:{id:"$(block)",count:1}}
