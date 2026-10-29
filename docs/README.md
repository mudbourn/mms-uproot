# mms-uproot assessment

Assessment of the mods in the pack that have no Fabric 26.3 build, and what each one means for **mms-uproot**: a load-time layer that rewrites the original jars in memory so they run on 26.3, without redistributing them. Done 2026-10-29. Per-group detail is in the other files in this folder.

## Scope

- **Modrinth check:** done 2026-10-29. 258 mods are in the pack, and 82 of them have no Fabric 26.3 build.
- **Pack mods:** the 10 `mms-*` mods are ours, so they get ported directly and are left out here. That leaves **72 third-party mods**.
- **Arc:** Arc's metafile has no update tracker, so the check missed its 26.1.2 and 26.2 builds.

## The main finding

Most of the 72 already have a 26.1 or 26.2 build. Those builds already use the unobfuscated names, and they carry most of the 1.21.11 to 26.x changes. Bridging from them to 26.3 is a much smaller job than bridging from 1.21.11. So mms-uproot is mostly a **26.1/26.2 to 26.3 bridge plus a metadata fixer**. A full bridge from 1.21.11 is needed for only a handful of mods.

| Bucket | Count | Mods |
|---|---|---|
| Already fine or replaceable on 26.3 | 6 | Peek (26.3 jar on GitHub), MapSyncer (26.3 beta on GitHub), Bubblellaneous (3.0.8 pack declares 26.3), Animatica Continuation → **Animatica Refabricated**, NoCreativeDrift → **please-stop**, Nametag Recipe Backport (in vanilla since 26.1, drop it) |
| Data only: widen the pack range, convert the JSON if needed | 12 | Luki's ×5, Towerinator, Unwrecked Ships, Happy Ghast but Fast, Invisible Armor Recipe, Training Dummy, Enchanted Utilities, Saddle Crafting (old ingredient syntax; rewrite it as our own datapack) |
| Bridge from the 26.1/26.2 build | ~44 | Everything not listed elsewhere in this table. See the group files |
| Bridge from 1.21.11 (no 26.x build) | 5 | Anvil Never Too Expensive, Arrow Entity Loot Drop, Disable Items (all easy); CorgiLib (medium, LGPL, so a fork is possible); Bountiful Fish (medium, CC0, blocked by terraform-wood-api) |
| Hard, **must be supported** (see below) | 4 | Bura, Mob AI Tweaks, Oh The Biomes We've Gone, Furnish |
| Highest risk among the newer-build bridges | 1 | Chloride: about 36 render mixins, all rights reserved, and its Sodium 0.9 config API is unchecked |

## The four hard mods (dropping them is not an option)

**Oh The Biomes We've Gone is the reason mms-uproot exists, and it is the first target.** Build order:
1. Generic plumbing: rewrite dependency checks, widen pack format ranges, recurse into jar-in-jar, soft-fail mixins with a report.
2. The intermediary → 26.3 remapper, including mixin annotation strings and access wideners.
3. Its dependency chain. Correction from the reference map in [bwg/](bwg/README.md):
   - Oh The Trees You'll Grow has a 26.3 build, but its API changed, so it needs shims.
   - terraform-wood-api is not a dependency.
   - So CorgiLib is the only dependency to bridge.
4. Oh The Biomes We've Gone itself, plus a load test of its 599 NBT files and its worldgen data.
5. **Nether Descent** (added 2026-10-29; not in the pack yet). It's by the same studio as BWG and reuses its material-rule, feature and OTTYG shims. Bridge it from its 26.2 build. See [nether-descent/](nether-descent/README.md).
6. Then the render layer, for Furnish, Bura and Mob AI Tweaks.

These four set how capable mms-uproot has to be. They are the reason it needs a render-API compatibility layer and per-mixin fallback, not just remapping.

| Mod | Bridge from | What it needs |
|---|---|---|
| **Oh The Biomes We've Gone** | 1.21.11 | Its dependency chain has to be bridged too: CorgiLib (one `@Overwrite` on a DynamicOps wrapper, a `LevelChunk` constructor hook, a scroll hook), Oh The Trees You'll Grow, and terraform-wood-api. TerraBlender and GeckoLib already have 26.3 builds. Its datapack side has 599 NBT files, 189 of them in an odd gzip form, which needs a load test. Upstream's unreleased `26.1.2-Update` branch is open source, so use it as a **reference** for what changed. |
| **Bura** | 1.21.11 | 11+ mixins on `ServerChunkCache`, `SectionRenderDispatcher`, `ParticleEngine`, `BlockEntityRenderDispatcher.submit` and the texture atlas. Needs the render compatibility layer and hand-retargeted chunk mixins. |
| **Mob AI Tweaks** | 1.21.11 (its 26.2 beta is broken) | 135 mixins across 263 vanilla classes. Most are AI goals and entity logic (remap plus rename table). The 33 render mixins go through the render layer. It also uses `FabricBrewingRecipeRegistryBuilder` (brewing shim). Volume is the main cost here. Needs per-mixin pass/fail reporting so a missing target turns off one feature, not the whole mod. |
| **Furnish** | 1.21.11 (or upstream's `feature-26.2` branch, if it ships first) | 15 block-entity renderers built on the pre-26.2 `MultiBufferSource` API. This is the clearest case for the render layer. The licence is unclear (jar says ARR, GitHub says CC0), so treat it as bridge-only. |

What this adds to mms-uproot's design:
- **Render compatibility layer:** emulate `MultiBufferSource`, `Tesselator`, `VertexConsumer` usage and the old `BlockEntityRenderer.render(...)` signature on top of the 26.2+ submit/extract pipeline (`SubmitNodeCollector`, `RenderTypes`). Furnish, Bura and Mob AI Tweaks all need it, and so does any other 1.21.11 jar that draws anything.
- **Per-mixin soft-fail with a report:** when an injection target is missing in 26.3, log it and skip that mixin instead of crashing. Then shim the ones that matter, one by one.
- **Dependency chains:** CorgiLib, Oh The Trees You'll Grow and terraform-wood-api become first-class uproot targets, not side work.
- **Reference diffs:** where upstream has an open-source 26.x branch (Oh The Biomes We've Gone, Furnish), diff it against 1.21.11 to write the shims. Use it only as a reference, never ship its code.

## Choke points (libraries that others need)

| Library | 26.3 status | What falls without it |
|---|---|---|
| **Knot** (the new DAQEM base lib) | newest is 26.2; no 26.3 work yet | Arc, Item Restrictions, Jobs+, Jobs+ Tools, **mms-jobs** |
| **owo-lib** | 26.2 only; 100 mixins; hit by 26.3's switch from GLFW to SDL | 7 Expanded Enchanting mods, Water Resistance Potion |
| Cobweb → Prometheus | 26.2 | Soul Fire'd |
| bad packets | 26.2 | WTHIT |
| Thermoo 10 (breaking API) | 26.1.x | Frostiful and Scorchful, which must move together with it |
| Perspective API | 26.2 | Leawind's Third Person |
| Origins Legacy: Classes | 26.2 | **mms-origins** |

The DAQEM stack (Arc, Item Restrictions, Jobs+) was rewritten for 26.x, not just ported. It dropped Architectury for Knot, and Arc's data format changed (`exp_level` → `exp_cost`, plus `arc:calculate` rewards). Bridging their 19.x jars is the wrong route. Use the 21.x builds, and rebuild **mms-jobs** against them. Our jobs config also needs re-checking, because it relies on the old defaults built into those jars.

## What mms-uproot has to handle for every mod

1. **Dependency check rewrite in `fabric.mod.json`.** Widen the `minecraft` ranges, and the `java` range too. Even the 26.x builds say `~26.2` or `<26.3`. Rewrite `fabric` to `fabric-api`, and alias removed Fabric API module ids such as `fabric-resource-loader-v0`.
2. **Pack format ranges.** Widen `pack_format`, `supported_formats` and `min_format`/`max_format` in the `pack.mcmeta` of mod-bundled packs and datapacks.
3. **Remapping, for the 1.21.11 jars only.** Remap intermediary → 1.21.11 Mojang → 26.3 names. The [1.21.11 intermediary file](https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.11/intermediary-1.21.11-v2.jar) is published. The Mojang mappings come from the version JSON. **Almost none of the jars ship a refmap**, so the intermediary names are written directly into the mixin annotation strings (`@Mixin`, `method=`, `target=`, `@Shadow`, `@Accessor`) and into the access wideners. All of those have to be rewritten. Lambda targets have to become javac names (`lambda$name$N`).
4. **Hand-made rename table for official names that changed between versions.** Examples: `ResourceLocation`→`Identifier`, `GuiGraphics`→`GuiGraphicsExtractor` with `render*`→`extract*`, `EntityType.X`→`EntityTypes.X` (same for `BlockEntityType`), entity classes moved into subpackages, `EnderMan`→`Enderman`, and `ChunkPos.toLong`→`pack`. Also build in Fabric API's 26.1 class-rename map, which has 139 entries.
5. **26.3-specific shims:**
   - GLFW → SDL input constants (`InputConstants`).
   - Recipes and advancements now load as reloadable registries.
   - `AxeItem`, `HoeItem` and `ShovelItem` are removed, so `instanceof` checks need rewriting.
   - `FabricBrewingRecipeRegistryBuilder` is removed, so a shim should generate brewing recipe JSON instead.
   - `worldgen/configured_feature` → `worldgen/feature` path conversion.
   - `Holder` is now sealed.
6. **Loader plumbing:**
   - Recurse into jar-in-jar (DC Integration nests 5 jars, Peek nests Patbox libs).
   - Honour mixin config plugins that add mixins at runtime.
7. **Not fixable by remapping; needs per-mod work or a drop:**
   - Subclasses of vanilla classes: Fake AFK extends `ServerGamePacketListenerImpl`, and Leashable's proxy entity extends `Turtle`.
   - `@Overwrite` on worldgen internals or on synthetic lambdas (Noisium, CorgiLib).
   - Render-pipeline mixins: 26.2 removed `MultiBufferSource` and `Tesselator` and added Vulkan.

## Licences

Most of the 72 are open source (MIT, LGPL, GPL or CC0). For those, porting from source or forking is legally an option alongside bridging. The ones that are all rights reserved can only be bridged in memory. They include Chloride, Crawl on Demand, Enchanted Utilities, Saddle Crafting, Trigger Happy Ghast and possibly Furnish (its jar says ARR, its GitHub says CC0).

## Still unverified

- 26.3 vanilla internals in general. No 26.3 jar or deobfuscated source was opened, and the 26.3 pack format numbers weren't confirmed.
- Whether each 26.2 build actually loads on 26.3 once its dependency checks are widened. That needs a smoke-test run.
- **Sinytra Connector:** how it rewrites mixins and stands in for missing methods.
- **Fabric Loader 0.19:** which hooks it offers for transforming mod jars at load time.
- Whether Fabric enforces `pack.mcmeta` ranges on packs bundled inside mods.
- MCEF on 26.3 (Computerpc).
- Whether `legacy_single_pool_element` still exists, which matters for Luki's Grand Capitals and Woodland Mansions.
