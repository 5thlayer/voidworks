# 5×5×3 of stone, 72 once the Displacer, its output hopper and chest are set in it: more than it
# harvests, on a floor it never harvests.
fill ~12 ~ ~-8 ~16 ~2 ~-4 minecraft:stone
# Set in the mound's face toward the sign, so its output hopper and chest show below it.
setblock ~14 ~ ~-4 minecraft:chest[facing=south]
setblock ~14 ~1 ~-4 minecraft:hopper[facing=down]
setblock ~14 ~2 ~-4 voidworks:overworld_void_displacer
setblock ~14 ~3 ~-4 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal_block",count:64}]}
