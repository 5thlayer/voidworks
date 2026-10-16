# Emptying the Void Siphon: a chest holding one already charged and toggled to empty, beside a bare
# patch of Voidstone. A player takes it and stands on the patch, and it empties into the network.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"void_siphon_emptying"}
fill ~12 ~ ~-8 ~16 ~ ~-4 voidworks:voidstone
setblock ~14 ~ ~-2 minecraft:chest{Items:[{Slot:13b,id:"voidworks:void_siphon",count:1,components:{"voidworks:siphon_motes":[{grade:5,count:32}],"voidworks:siphon_mode":"empty"}}]}
