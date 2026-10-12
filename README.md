# Voidworks

Voidworks allows harvesting **void** from the Minecraft dimensions. Each dimension has its own void harvesting mechanics which involves placing specific blocks of different density either manually or through a Void Displacer. The placed blocks yields void energy infused motes and leaves indestructible Voidstone behind. Void Motes then travel through Voidstone blocks and can be stored in Void Wells. Void Dragons can be bred using Ender Dragon's eggs and then used as an interdimensional travel transport or to mass transport Void Motes between dimensions. The Void Motes can be spent on endgame gear and to fuel the **Mutation** mechanics: upgrading an item up its Family, or breaking it down into its ingredients.

## Why?
Because void asks to be filled!

**NeoForge, Minecraft 26.1.2 only.** Voidworks is in design: nothing is playable yet. The design is [docs/spec/void-energy.md](docs/spec/void-energy.md), and the terms are in [GLOSSARY.md](GLOSSARY.md).

## What sets it apart

- **Void never becomes ordinary items.** Unlike EMC or UU-matter, motes buy power, Mutation and void-only materials. Mutation is the only path from motes to an ordinary item, and it needs the item's Family or recipe to start from.
- **Harvesting consumes the world.** Every harvested position becomes Voidstone and every Displacer ends as Voidstone, so void is bounded by the world you are willing to spend.
- **Void runs downhill.** A mote is worth more the higher the Void Pressure where it was harvested, and the lower the Pressure where it is spent. The End is the reservoir and the Nether the turbine, and moving motes between them is the logistics game.

## For pack authors

Planned, all as data: the Density of each block by tag, the displacement table that decides what an Overworld Displacer returns for a terrain block, the Families an upgrade climbs, and overrides for breakdowns.

## Dependencies

None beyond NeoForge.

## License

MIT. Source is on [GitHub](https://github.com/5thlayer/voidworks).
