# A Chamber's scene: the Chamber built awake, fed from a hopper on its east side, clear of what it
# shows over it, and emptied by a hopper below into a chest, with 18 Voidstone beside it full of
# motes to pay for its work.
$setblock ~14 ~1 ~-6 voidworks:$(chamber)[dormant=false]{dormancy:{awake:1b}}
$setblock ~15 ~1 ~-6 minecraft:hopper[facing=west]{Items:[{Slot:0b,id:"$(item)",count:$(count)}]}
setblock ~14 ~ ~-6 minecraft:hopper[facing=south]
setblock ~14 ~ ~-5 minecraft:chest
fill ~12 ~ ~-7 ~13 ~2 ~-5 voidworks:voidstone
voidworks mote ~13 ~1 ~-6 5
