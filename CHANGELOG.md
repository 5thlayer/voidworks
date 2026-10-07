# Changelog

Written for Consumers: what a mod building against Voidworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- Void Pressure now reads from a data map, `voidworks:void_pressure`, keyed by dimension (`data/<namespace>/data_maps/dimension/void_pressure.json`). Each entry gives a `base` pressure and a `harvest_kind` (`end`, `overworld`, `nether` or `none`); Voidworks ships the three vanilla dimensions, and a Consumer or datapack adds or overrides an entry. A dimension the map does not name takes the Overworld's pressure and harvest kind `none`. Read it with `VoidPressure.at(level, pos)` and `VoidPressure.harvestKind(level)`; the numbers are provisional.
