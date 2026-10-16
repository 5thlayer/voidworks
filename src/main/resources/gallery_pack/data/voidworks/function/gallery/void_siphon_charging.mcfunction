# Charging the Void Siphon: a chest holding an empty one beside a patch of Voidstone full of motes.
# A player takes it and stands on the patch, and it charges from the network; the scene gives nothing.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"void_siphon_charging"}
fill ~12 ~ ~-8 ~16 ~ ~-4 voidworks:voidstone
# One grade above the End's Void Pressure over the open void, worth something wherever it is built.
voidworks mote ~14 ~ ~-6 5
setblock ~14 ~ ~-2 minecraft:chest{Items:[{Slot:13b,id:"voidworks:void_siphon",count:1}]}
