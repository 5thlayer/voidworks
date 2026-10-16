# The Void Siphon: a chest holding one, a full patch of Voidstone to charge it from and a bare one to
# empty it into, and a sign saying what to do. A player takes the Siphon; the scene gives nothing.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"void_siphon"}
fill ~7 ~ ~-8 ~9 ~ ~-6 voidworks:voidstone
fill ~19 ~ ~-8 ~21 ~ ~-6 voidworks:voidstone
voidworks mote ~8 ~ ~-7 5
setblock ~14 ~ ~-3 minecraft:chest{Items:[{Slot:13b,id:"voidworks:void_siphon",count:1}]}
