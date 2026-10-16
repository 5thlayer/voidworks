# One Density's Displacer at ~x, the one stone under it ringed by four Voidstone: room for 80 motes,
# more than a diamond block's 64.
$setblock ~$(x) ~ ~-6 minecraft:stone
$setblock ~$(x) ~ ~-7 voidworks:voidstone
$setblock ~$(x) ~ ~-5 voidworks:voidstone
$execute positioned ~$(x) ~ ~-6 run setblock ~-1 ~ ~ voidworks:voidstone
$execute positioned ~$(x) ~ ~-6 run setblock ~1 ~ ~ voidworks:voidstone
$setblock ~$(x) ~1 ~-6 voidworks:overworld_void_displacer
$setblock ~$(x) ~2 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"$(block)",count:1}]}
