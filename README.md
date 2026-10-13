![Voidworks](https://raw.githubusercontent.com/5thlayer/voidworks/main/publish/voidworks-cover.png)

Voidworks lets you harvest **void** from the Minecraft dimensions. Each dimension gives up its void its own way: you feed dense blocks to a Void Displacer, or to a Void Siphon by hand, and every harvest yields void-infused **motes** and leaves indestructible **Voidstone** behind. Motes fly off as butterflies into the Voidstone network, which stores them, and you spend them to **upgrade items** up their Family: copper ingots into iron, iron into gold.

## Why?

Because void asks to be filled!

**NeoForge, Minecraft 26.1.2 only.** Voidworks is early work: harvesting, Voidstone networks and upgrading are in, the Void Well, the Void Dragon and breaking items down are still to come. Please report anything odd on [GitHub](https://github.com/5thlayer/voidworks/issues).

To play Voidworks beside every other FactoryWorks mod, install the [FactoryWorks Showcase](https://www.curseforge.com/minecraft/modpacks/factoryworks-showcase) modpack from CurseForge.

## What sets it apart

- **Void never becomes ordinary items.** Unlike EMC or UU-matter, motes buy upgrades, not items from nothing. An upgrade needs the item a Family starts from.
- **Harvesting consumes the world.** Every harvested position becomes Voidstone and every Displacer ends as Voidstone, so void is bounded by the world you are willing to spend.
- **Void runs downhill.** A mote is worth more the higher the Void Pressure where it was harvested, and the lower the Pressure where it is spent. The End is the reservoir and the Nether the turbine, and moving motes between them is the logistics game.

## Features

- **Three Void Displacers, one per dimension.** The Overworld Displacer swaps fed blocks into the terrain around it and gives you that terrain back. The End Displacer grows its body out into the air, and harvests higher over the open void. The Nether Displacer drinks lava lakes and fills its tank, which any pipe can drain. Each harvests a fixed number of times, then turns to Voidstone.
- **Density.** What you feed sets how many motes a harvest yields: copper and coal blocks are the lightest, diamond and netherite blocks the densest.
- **Voidstone networks.** Touching Voidstone is one network holding one pool of motes, and every Voidstone adds room.
- **The Void Siphon.** A wand that harvests by hand, a step below a Displacer, and carries motes between networks and dimensions. Standing on Voidstone, it charges from the network or empties into it.
- **Dormant machines.** The End and Nether Displacers and the Mutation Chamber are placed Dormant and wake once their cost in void energy is paid, by the networks touching them or by a Siphon.
- **The Mutation Chamber.** Upgrades items up their Family for motes, with a chance per upgrade. It has no screen: hoppers and belts feed and empty it.
- **The Void Crucible.** Upgrades for free, slowly. It is fastest in the End and slowest in the Nether, and Crucibles working near each other share one rate.
- **Jade support.** Jade shows a network's motes and what they are worth where you stand, a machine's upgrade, progress and what stalls it, and how much of a Dormant machine's cost is paid. Voidworks runs without Jade.

## For pack authors

- **Density** is item tags, `#voidworks:density/1` to `#voidworks:density/4`.
- **The displacement table**, the `voidworks:displacement` data map on blocks, decides what an Overworld Displacer gives back for a terrain block at each Density. Voidworks ships no entries, so it gives the terrain block itself.
- **Families** are the `voidworks:family` datapack registry: the ladders an upgrade climbs, each Rung with its count, cost and chance. Voidworks ships metals, raw metals, gems, dusts and mob drops, with a tag per Rung that takes in the `c:` tags, so other mods' items join by tag.
- **Void Pressure** and each dimension's harvest kind are the `voidworks:void_pressure` data map, and Dormant costs the `voidworks:dormant_cost` data map.
- **Numbers** such as a Displacer's harvests, Voidstone's capacity and the Crucible's speed are in `voidworks-server.toml`. They are provisional while Voidworks is below 1.0.

The design is [docs/spec/void-energy.md](docs/spec/void-energy.md), and the terms are in [GLOSSARY.md](GLOSSARY.md).

## Dependencies

None beyond NeoForge. Voidworks nests libworks-runtime, so there is nothing more to install. Jade is optional.

## License

MIT. Source is on [GitHub](https://github.com/5thlayer/voidworks). The block and Siphon sprites are from Malcolm Riley's [unused-textures](https://github.com/malcolmriley/unused-textures), under CC BY 4.0; [NOTICE](NOTICE) credits each.
Releases are on [CurseForge](https://www.curseforge.com/projects/1742531).
