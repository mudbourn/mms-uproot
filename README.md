# mms-uproot

A Fabric compatibility layer for the MMS pack. When the pack moves to Minecraft 26.3, some mods have no 26.3 build. mms-uproot rewrites their original, unmodified jars (1.21.11, 26.1 or 26.2 builds) in memory at load time so they run on 26.3. It never redistributes them.

Status: **planning and analysis only.** There's no mod code yet.

## Layout

- `docs/`: the assessment and the plan.
  - [README.md](docs/README.md): the main plan. It covers all 72 mods with no 26.3 build, the generic work every mod needs, the build order, and the four hard mods that must be supported.
  - `worldgen.md`, `libs-jobs.md`, `content-a.md`, `content-b.md`, `survival-server.md`, `client.md`: the assessment for each group of mods.
  - [bwg/](docs/bwg/README.md): the reference map for Oh The Biomes We've Gone and CorgiLib. BWG is the first target.
  - [nether-descent/](docs/nether-descent/README.md): the reference map for Nether Descent, the second target.
- `tools/uproot-map/`: checks every reference a mod jar makes against the real 26.3 jars and reports what survives. Both 1.21.11 (intermediary) and 26.x (Mojang-name) jars work.
- `refdata/` (gitignored, about 180 MB): the 26.3 game jars, the 1.21.11 mappings and the 26.3 library jars the tool reads.

## Running the mapper

```
python3 -I tools/uproot-map/fetch_mc.py refdata     # only needed if refdata/ is missing
python3 -I tools/uproot-map/fetch_libs.py refdata
python3 -I tools/uproot-map/uproot_map.py refdata out <mod.jar> [<mod.jar> ...]
```

## Build order

1. Groundwork every mod needs:
   - rewrite the Minecraft version ranges in `fabric.mod.json`
   - widen pack format ranges
   - recurse into jar-in-jar
   - skip and report failing mixins instead of crashing
2. A remapper for 1.21.11 intermediary jars, which also rewrites mixin annotation strings and access wideners.
3. CorgiLib, plus shims for the new Oh The Trees You'll Grow API.
4. Oh The Biomes We've Gone: surface rules → material rules, and the feature-API merge.
5. Nether Descent, from its 26.2 build.
6. The render compatibility layer, for Furnish, Bura and Mob AI Tweaks.
