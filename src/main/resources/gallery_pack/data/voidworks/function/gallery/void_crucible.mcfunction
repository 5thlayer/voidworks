# The Void Crucible: copper ingots upgraded to iron for free, slowly, fed from a hopper above and
# emptied by a hopper below into a chest.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"void_crucible"}
setblock ~14 ~1 ~-6 voidworks:void_crucible
setblock ~14 ~2 ~-6 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:copper_ingot",count:4}]}
setblock ~14 ~ ~-6 minecraft:hopper[facing=south]
setblock ~14 ~ ~-5 minecraft:chest
