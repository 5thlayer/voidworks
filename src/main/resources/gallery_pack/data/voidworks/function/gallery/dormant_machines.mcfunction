# Dormant machines: a Dormant Mutation Chamber at the end of a line of eight Voidstone, motes put in
# at the line's far end. They crawl to it, it soaks its cost from them, and wakes.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"dormant_machines"}
fill ~6 ~ ~-6 ~13 ~ ~-6 voidworks:voidstone
setblock ~14 ~ ~-6 voidworks:mutation_chamber
# One grade above the End's Void Pressure over the open void: 16 pay its shipped cost anywhere.
voidworks mote ~6 ~ ~-6 5 16
