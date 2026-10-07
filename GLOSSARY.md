# Voidworks

Voidworks harvests void from the world itself, at a permanent cost to the world, moves it between dimensions on dragons, and spends it to power gear and mutate items. It requires nothing but Minecraft and NeoForge. Its first Consumer is the FactoryWorks Pack, whose Showcase fills the displacement table. The design is [docs/spec/void-energy.md](docs/spec/void-energy.md).

## Language

The Library's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Voidworks**:
This Library: harvesting, Voidstone networks, the Void Dragon and Mutation. It requires nothing but Minecraft and NeoForge.

**Consumer**:
A mod that builds against this Library: the FactoryWorks Pack, or another Library.
_Avoid_: client (collides with the game's client side), dependent, integration

### Void

**Mote**:
The unit of void, harvested, stored and moved. A mote carries a grade: one step above the Void
Pressure where it was harvested, so it is always worth something there. Grades run from the lowest
any harvest gives, which a mote nothing has graded also has, to a fixed maximum. Motes of one grade stack; motes of different grades never merge.

**Void energy**:
What a mote releases when it is spent: the more its grade exceeds the Void Pressure where it is
spent, the more it releases, doubling with each step. Nothing stores void energy; everything stores motes. It is not FE.
Bare "void" means this mechanic; removing items is "destroying" or "trashing" them.
_Avoid_: voiding (for destroying items), void as "needs no power"

**Void Pressure**:
The ambient void level of a place, a small integer: a base per dimension, highest in the End, then
the Overworld, lowest in the Nether, and one step higher where the End is open to the void below. It sets the grade of motes harvested
there, the value of motes spent there, how fast a Void Crucible works there, and how fast a Void
Dragon weakens there. Motes stored nearby never change it.
_Avoid_: network pressure (the motes' own, which only steers flow)

**Harvest kind**:
What a dimension harvests, named by its Void Pressure entry: `end`, `overworld`, `nether`, or `none`
for a dimension that harvests nothing. The Void Siphon behaves by it.

**Density**:
How many motes a block yields when fed to a Void Displacer. It is set per block, by tag, and
steepens sharply with each step: stone is cheap, and storage blocks are how harvesting scales.

### Harvesting

**Voidstone**:
The indestructible block a harvested position becomes. Every harvest leaves one, so harvesting
permanently consumes the world, which is what bounds void. Touching Voidstone forms a network that
carries motes.
_Avoid_: bedrock, residue

**Void Displacer**:
A machine that harvests motes in its zone, attuned to the dimension it is built for and working
only there. It cannot be broken or moved while its zone has anything left to convert; when nothing
is left it becomes Voidstone itself.
_Avoid_: harvester, Void Well

**Void Siphon**:
The item a player harvests motes with by hand, at a far worse yield than a Void Displacer's. It
holds motes, and void gear draws from it.
_Avoid_: glove

### Moving motes

**Void Well**:
A large store of motes on a Voidstone network, and the stop where the Void Dragon loads, unloads
and heals.
_Avoid_: roost, Void Displacer

**Void Dragon**:
A dragon raised on void that carries motes between Void Wells, diving through the void between
them, across dimensions. It grows from a Dragonling.
_Avoid_: Ender Dragon (the boss), tamed dragon

**Dragonling**:
A young Void Dragon, hatched from an egg soaked in motes. It cannot carry yet.

**Drained Dragon Egg**:
The egg every dragon kill after the first drops: a dragon egg drained of void, which hatches once
it has soaked up enough motes.
_Avoid_: lesser egg

### Mutation

**Mutation**:
Turning an item into another: upgrading it to the next tier of its Family, or breaking it down into
its ingredients. A Mutation Chamber does it quickly for motes; a Void Crucible upgrades items
slowly and for free. An upgrade is a chance roll that keeps the item on failure, and a breakdown
never returns more than went in.
_Avoid_: transmutation, recycling

**Mutation Chamber**:
The machine that performs a Mutation, spending motes.

**Void Crucible**:
A block that holds items in Void Pressure until they upgrade up their Family. It spends no motes,
only time, and progresses only while its chunk is loaded.
_Avoid_: altar, cradle

**Family**:
An ordered ladder of materials that an upgrade climbs, such as copper → iron → gold. Families are
data, so other mods' materials can join one.
_Avoid_: tier, quality
