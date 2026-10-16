# A Displacer built outside its own dimension drops as its item on its first tick, so a scene built
# elsewhere shows it as an item display over a pedestal behind the sign, which names its dimension.
# A display, not an item frame: a frame's NBT names its block absolutely, which a function cannot.
setblock ~14 ~ ~-2 minecraft:smooth_stone
$execute align xyz run summon minecraft:item_display ~14.5 ~1.5 ~-1.5 {item:{id:"$(item)",count:1},billboard:"vertical"}
