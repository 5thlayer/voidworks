# Changelog

Written for Consumers: what a mod building against Voidworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- Void Pressure now reads from a data map, `voidworks:void_pressure`, keyed by dimension (`data/<namespace>/data_maps/dimension/void_pressure.json`). Each entry gives a `base` pressure and a `harvest_kind` (`end`, `overworld`, `nether` or `none`); Voidworks ships the three vanilla dimensions, and a Consumer or datapack adds or overrides an entry. A dimension the map does not name takes the Overworld's pressure and harvest kind `none`. Read it with `VoidPressure.at(level, pos)` and `VoidPressure.harvestKind(level)`; the numbers are provisional.
- The mote item, `voidworks:mote`: its grade is the `voidworks:grade` data component on the stack, so motes of one grade stack and motes of different grades never merge. `MoteItem.stack(grade, count)` makes a stack and `MoteItem.gradeOf(stack)` reads it; the name shows the grade.
- Spending motes: `VoidSpender.spend(inventory, level, pos, required)` releases at least `required` void energy from any NeoForge item inventory (`ResourceHandler<ItemResource>`, such as `PlayerInventoryWrapper.of(player)` or `VanillaContainerWrapper.of(container)`), at the Void Pressure where the spender stands (`pos`). It takes the lowest grade still above the pressure first and moves up the grades only as needed, so it can release more than `required`; when the inventory cannot afford it, nothing is taken and the result is empty. A result carries the motes taken per grade and the energy released. `VoidEnergy.plan` is the same rule as plain logic on grade-to-count maps; the energy per mote is provisional.
