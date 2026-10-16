# 5×5×3 of stone, 72 once the Displacer, its output hopper and chest are set in it: more than it
# harvests, on a floor it never harvests.
fill ~12 ~ ~-8 ~16 ~2 ~-4 minecraft:stone
setblock ~14 ~ ~-6 minecraft:chest
setblock ~14 ~1 ~-6 minecraft:hopper[facing=down]
setblock ~14 ~2 ~-6 voidworks:overworld_void_displacer
setblock ~14 ~3 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal_block",count:64}]}
