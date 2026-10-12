---
status: accepted
---

# Breakdown is a block of its own, with no screen

The spec had the Mutation Chamber do two things for motes, upgrade and breakdown, with breakdown's
yield rising with the motes spent on it. The Chamber has no screen, and its item face takes only items
with an upgrade, so nothing said which of the two it does to an item, nor how much a player spends.

**Decision.** Breakdown moves to a block of its own, the Unmaking Chamber, and the Mutation Chamber
only upgrades. Neither has a screen or holds a recipe: the item decides what it becomes, its Family's
next Rung in one and its one crafting recipe, read in reverse, in the other. The spend a player cannot
choose becomes the Unmaking Chamber's own history: its yield starts at a quarter and rises with the
motes it has spent in its life, toward a cap below all of them, and it forgets that when broken.

## Considered options

- **A setting on the Chamber, cycled by sneak-use**: state a player cannot see, which a misclick
  changes and a hopper feeding it cannot know.
- **A held-recipe screen, as Craftworks' Assembler and Fieldworks' Harvester have**: their recipe is
  set only through a recipe viewer, which Voidworks, requiring nothing but Minecraft and NeoForge,
  cannot require. It would also pick what the item already decides, could not choose a spend, and
  would need a Chamber for each Rung, where one Chamber looped back to itself climbs a whole ladder.
- **A screen with a mode toggle and a yield selector**: workable, with no recipe viewer, but a screen
  and its packets for a choice two blocks make without one.

## Consequences

- A base that breaks items down pays a second Dormant cost.
- Each block's item face takes only what it can work on, so a belt feeding either needs no filter.
