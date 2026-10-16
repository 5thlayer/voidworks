# Density: four Overworld Displacers, each fed one block of a Density from coal to diamond, each
# harvesting the one stone under it into a network of four Voidstone around that stone, then turning
# to Voidstone itself. Jade on each network shows the motes its one harvest yielded: 1, 4, 16, 64.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"density"}
execute if dimension minecraft:overworld run function voidworks:gallery/density/one {x:5,block:"minecraft:coal_block"}
execute if dimension minecraft:overworld run function voidworks:gallery/density/one {x:10,block:"minecraft:iron_block"}
execute if dimension minecraft:overworld run function voidworks:gallery/density/one {x:15,block:"minecraft:gold_block"}
execute if dimension minecraft:overworld run function voidworks:gallery/density/one {x:20,block:"minecraft:diamond_block"}
execute unless dimension minecraft:overworld run function voidworks:gallery/elsewhere {item:"voidworks:overworld_void_displacer"}
