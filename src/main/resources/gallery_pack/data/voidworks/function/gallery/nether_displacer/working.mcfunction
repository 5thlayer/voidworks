# The pool's wall holds the lava on the floor; the Displacer, built awake, takes the wall's east
# side.
fill ~10 ~ ~-8 ~14 ~ ~-4 minecraft:smooth_stone
fill ~11 ~ ~-7 ~13 ~ ~-5 minecraft:lava
setblock ~14 ~ ~-6 voidworks:nether_void_displacer[dormant=false]{dormancy:{awake:1b}}
setblock ~14 ~1 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal_block",count:16}]}
