# Findings: worldgen group (12 mods)

Jars inspected under work/<slug>/. Data-format facts for 1.21.11 -> 26.x come from minecraft.wiki Pack_format and Data_pack pages (1.21.11 = pack 94.1, 26.1 = 101.1, 26.2 = 107.1, 26.3 = 121.0; the wiki has NO changelog for 26.3). Where the wiki is silent I say "unverified".

## Table

| Slug | Kind | License / source | Mixins | Verdict |
|---|---|---|---|---|
| oh-the-biomes-weve-gone (BWG 4.4.2) | big content mod + worldgen | ARR; source public (GitHub Potion-Studios, ARR) | 10 (6 fabric, 4 common); no @Overwrite/@Redirect | hard (wait for upstream; branch `26.1.2-Update` exists, WIP) |
| corgilib 9.0.0.1 | library | LGPL-3.0; public (CorgiTaco/CorgiLib) | 7 (1 client); 1 @Overwrite | medium (or fork, LGPL) |
| lukis-ancient-cities 1.2 | datapack in jar | ARR; no source | 0 | easy |
| lukis-crazy-chambers 1.0.3 | datapack in jar | ARR | 0 | easy |
| lukis-grand-capitals 1.1.3 | datapack in jar | ARR | 0 | easy (watch legacy pool elements) |
| lukis-strongholds 1.0 | datapack in jar | ARR | 0 | easy |
| lukis-woodland-mansions 1.0 | datapack in jar | ARR | 0 | easy |
| unwrecked-ships 2.0.6 | datapack in jar | MIT; GitHub Wpgradford/Unwrecked-Ships | 0 | easy |
| noisiumforked 2.8.3 | server worldgen perf | LGPL-3.0; public (coredex-source/noisium) | 5 (2 @Overwrite, 2 @Redirect) | use-newer-build (2.8.5+mc26.2 exists) |
| bountiful-fish 1.1.1 | content mod | CC0-1.0; no repo listed (fabric-example-mod URL) | 8 (3 client) | medium (CC0, so fork/port also OK) |
| furnish-furniture 31 | content mod | ARR in jar and Modrinth; GitHub API reports CC0-1.0 (conflict) | 1 (client, trivial) | use-newer-build (GitHub `feature-26.2` branch, unreleased) |
| towerinator 1.3 | datapack in jar | ARR; no source | 0 | easy |

Nothing here has a Fabric build newer than 1.21.11 on Modrinth except noisium (26.2). Newest-MC list in targets.tsv for the lukis packs is stale (Modrinth shows 1.21.11 builds); same jars.

## Cross-cutting: worldgen / datapack format 1.21.11 -> 26.x

- Changes that matter (wiki): 26.1 (101.1) villager trades, wolf sounds, block/fluid tag renames driving crop support/growth, bone-meal features data-driven, count placement modifier cap 256 -> 4096. 26.2 (107.1): `weird_scaled_sampler` density function REMOVED (replaced by `interval_select`); `noise_gradient` surface rule REMOVED (replaced by `noise_threshold` + `is_3d`); `cannot_replace`/`invalid_blocks`/`root_replaceable`/`replaceable`/`infiniburn` accept IDs as well as tags; extra `lake` fields; entity predicates become component-map style and reject unknown fields. 26.3 (121.0): undocumented.
- Structures, template pools, processor lists, structure sets, jigsaw structure JSON: wiki lists NO changes across 1.21.11-26.2. Unverified for 26.3.
- Grepped every JSON in the 8 data-bearing mods: none use `weird_scaled_sampler`, `noise_gradient`, density functions, noise settings or noise routers. So the removals do not hit this group.
- pack.mcmeta in these jars is old style (`pack_format` 15/16/61 plus `supported_formats`, max 99-100, all below 101). Towerinator uses `min_format`/`max_format` [94,0]-[95,0]. These are mod-bundled packs loaded by Fabric's resource loader, which should not apply the version-compat filter, but I did not verify that against 26.3 Fabric API. Cheap insurance: uproot can rewrite pack.mcmeta in memory to widen the range.
- Fabric API id risk: lukis/towerinator/unwrecked declare `fabric-resource-loader-v0` (lukis) as a dependency. If 26.3 Fabric API no longer provides that id, mod resolution fails before any data loads. Uproot should provide/alias it (provides-map), not touch the data. Unverified whether the id still exists.
- Structure NBT: old DataVersions load via DFU (`DataFixTypes.STRUCTURE`) at read time. DataVersions found: BWG 2865/2975/3120/3465/3955; ancient-cities 3955/4325; crazy-chambers 3953; grand-capitals 3120/3465/3700/4082; strongholds 4325; mansions 3465/4325; unwrecked 3337/3700/4189; towerinator 4189 (1.21.11 is about 4671). All older than 1.21.11 and already loadable in 1.21.11, so the DFU chain is the same one 26.x continues; no conversion expected. Worth one smoke test per pack on 26.3 (block-entity/item component upgrades in chests/spawners/signs inside templates are the usual casualty). No in-memory NBT rewrite needed in principle.
- BWG NBT quirk: 189 of 599 `.nbt` files fail `gzip -t` (header 1f8b, probably a missing/odd trailer). They load on 1.21.11 so Java GZIPInputStream tolerates them; verify on 26.x. Upstream's latest 26.1.2 branch commit says "Strip air blocks from tree structure NBT files", so upstream touched these for 26.x.
- Legacy cruft that is harmless: grand-capitals ships both `structures/` + `structure/`, `loot_tables/` + `loot_table/`, and a `configured_structure_feature/` dir (pre-1.19.x names; ignored by 26.x). `legacy_single_pool_element` is still a vanilla type (310 uses in grand-capitals, 18 in mansions); wiki shows no removal. Unverified for 26.3: if it is removed, an in-memory JSON rewrite `legacy_single_pool_element` -> `single_pool_element` + `"projection"` keeps it working.
- Biome JSON in BWG already uses the 1.21.11 `attributes` block (environment attributes), so no biome-format work from 1.21.11.

Generic uproot items from this group: (1) a pack.mcmeta widener, (2) Fabric API module-id aliases (`fabric-resource-loader-v0`, maybe `fabric-api` provides), (3) optional JSON rewriters for datapack changes keyed by pack format (none needed for current data), (4) for the 26.2 era, a JSON pass that would translate `weird_scaled_sampler`/`noise_gradient` if any other mod in the pack uses them.

## Per mod

### oh-the-biomes-weve-gone (BWG 4.4.2) -- hard
- License ARR, repo public but not forkable/redistributable. Depends: terrablender >=21.11, corgilib >=9.0.0.0, ohthetreesyoullgrow >=9.1.2, geckolib >=5.4.4. Dependency state on 26.x: terrablender (26.3.0.0.9 on Modrinth, 26.3 ok), geckolib (5.5.7 for 26.3), corgilib (NO 26.x), ohthetreesyoullgrow (not on Modrinth under that slug; not found).
- Footprint: 278 classes, about 690 distinct intermediary classes referenced. Registers blocks (wood sets, plants, bushes), entities (ManOWar, PumpkinWarden, Oddion, Wreath, villager professions/types), features, structures, 55 biomes, plus about 8000 JSON (984 loot, 832 worldgen, 599 NBT, 586 advancements, 569 recipes). Custom feature/placement types registered in code: arch, noise_sphere, glow_berry_decorator, attached_to_logs (OTTYG) and others.
- Mixins (targets): ChunkAccess, ChunkStatusTasks (method_57608), SurfaceSystem (method_39106), EatBlockGoal (method_6264/6268), GrassBlock bonemeal (class_2372, method_9652), and getStateForPlacement (method_9695) on VegetationBlock, AttachedStemBlock, CropBlock, PitcherCropBlock, StemBlock. All plain @Inject. Largest exposure: 26.1 made bone-meal features data-driven (GrassBlock mixin likely dead), block/fluid tag changes for crops (crop mixins), and the chunk-status/surface-system internals (which 26.x worldgen refactors may move).
- AW: ~30+ entries including Blocks property builders, `WoodType`/`BlockSetType` constructors, ChunkStatus-adjacent. Needs name-based remap plus checks that each member still exists (BlockBehaviour.Properties builder signatures changed between releases).
- Also needs the terrablender entrypoint (`terrablender` entrypoint type) and geckolib on 26.x. Client: custom ModelAccess/model code, Geckolib renderers -> exposed to the 26.x render-state/submit pipeline.
- Upstream: branches `26.1.2-Update` and `26.1.2-WIP` (latest commits 2026-09-23, not released on Modrinth, which tops out at 1.21.11). Best option: wait for upstream 26.x release. Bridging 1.21.11 -> 26.3 with 3 unavailable-or-unbridged dependencies is not realistic.
- Uproot needs: remap + shims for Block properties/crop/bone-meal, a TerraBlender entry (works, 26.3 build exists), and a corgilib port (see below). Mostly blocked by deps, not by BWG itself.

### corgilib 9.0.0.1 -- medium (library; hard dep of BWG)
- LGPL-3.0, source public; branches only up to 1.21.11 (last commit 2026-10-09, v5.0.0.10 ... line unrelated). No 26.x build. Because LGPL, forking/porting source is allowed and probably the cleaner path than bytecode bridging.
- Mixins: ServerLevel (method_18203 chunk unload hook), StructureBlock (method_55766, use), Entity, ChunkAccess, LevelChunk `<init>(ServerLevel, ProtoChunk, PostLoadProcessor)`, MouseHandler.method_1598 (onScroll(long,double,double), client), and `DelegatingOpsMixin` with an @Overwrite on class_5379 (a DynamicOps wrapper). Constructor-signature injection on LevelChunk and the Overwrite are the fragile pieces.
- AW: RegistryAccess/BuiltInRegistries internals (class_7923 method_47479 `registerSimple`-style, `class_6889` registration info), Inventory items field, Display.setTransformation, Screen.addRenderableWidget.
- Data: none (2 JSON files only). Bundles Jankson shaded. Registers: entity conditions (36 classes), blending-function codecs (`corgilib:ease_*` used by BWG worldgen JSON), network via platform layer.
- Uproot needs: remap, Screen.addRenderableWidget and MouseHandler scroll shim, retarget LevelChunk init mixin, rewrite or disable the DynamicOps @Overwrite.

### lukis-ancient-cities / crazy-chambers / grand-capitals / strongholds / woodland-mansions -- easy
- All five: ARR, no source, no code, no mixins, no libs. Fabric mod with `fabric-resource-loader-v0` dependency and `environment: *`; Modrinth also lists them as plain `datapack` loader projects, so a bare datapack drop-in is an alternative to uproot.
- Data counts: ancient-cities 47 NBT (+25 old `structures/` dir), crazy-chambers 52, grand-capitals 434 (217 in legacy dir), strongholds 46, mansions 20. Types used: jigsaw structures, `single_pool_element`/`legacy_single_pool_element`, rule processors, village/mansion/swamp_hut/pillager_outpost vanilla structure types.
- grand-capitals and mansions OVERRIDE vanilla ids (`minecraft:worldgen/structure/village_*`, `structure_set/villages`, `pillager_outpost`, `swamp_hut`, `mansion`). Fine as long as vanilla ids still exist in 26.x (assumed). Pack format values: 15-16 with supported 9-99/14-100.
- Risk: only 26.3 format changes (undocumented) and Fabric API id. Uproot needs: none beyond metadata aliasing.

### unwrecked-ships 2.0.6 -- easy
- MIT, source GitHub Wpgradford/Unwrecked-Ships (branch plus-1.21.4+). Datapack-only, provides `unwrecked_ships` and `unwrecked_ships_base`. 78 NBT (DataVersion 3337/3700/4189), 42 worldgen JSON: 216 `single_pool_element`, 3 `empty_pool_element`, 19 rule processors, 2 `random_spread` structure sets with unprefixed `"type":"random_spread"`. pack_format 61, supported 61-71.
- Uproot needs: pack.mcmeta widening only (and the load check above). Alternatively use as plain datapack.

### towerinator 1.3 -- easy
- ARR, no source (Zelanle/ALDgamer), datapack-only. 174 NBT all DataVersion 4189, 226 worldgen JSON (56 jigsaw structures, 56 `random_spread` sets, template pools, processors), 70 loot tables, load.json tag pointing at an EMPTY `tw:towerinator_load.mcfunction`. pack.mcmeta uses `min_format`/`max_format` [94,0]-[95,0] (the new format, 1.21.9+ style), so the widest-rejection risk is that range excluding 101/107/121 if it is ever enforced.
- Uses `liquid_settings`, `project_start_to_heightmap`, `use_expansion_hack`: all 1.21.x fields; no wiki change noted.
- Uproot needs: pack.mcmeta widening.

### noisiumforked 2.8.3 -- use-newer-build
- LGPL-3.0, repo coredex-source/noisium (Modrinth id noisiumforked, server-only). Newest Fabric builds: 2.8.5+mc26.2 and 2.8.5+mc26.1.2 (2026-05-30), branches `26.1` and `dev/26.2`. 26.3 build absent; the 26.2 -> 26.3 gap is what is left.
- 1.21.11 jar mixins (5): ChainedBlockSource (@Overwrite), ChunkSection (@Overwrite), GenerationShapeConfig/NoiseSettings (3 injects incl. ctor), NoiseBasedChunkGenerator (@Redirect on LevelChunkSection.setBlockState in doFill + @Overwrite of a `lambda$fillFromNoise$0` -- a synthetic lambda name, extremely brittle), and Lithium compat mixin (same redirect+overwrite). AW on LevelChunkSection fields (nonEmptyBlockCount etc.); 26.1 branch replaces it with a `LevelChunkSectionAccessor` mixin and uses official names.
- Bridging the 1.21.11 jar would require retargeting a lambda-name @Overwrite, which will break whenever Mojang reshapes the lambda. Do not bridge; take the 26.2 jar and bridge the small 26.2 -> 26.3 gap, or fork the source (LGPL).
- Uproot needs: nothing for this mod if using 26.2 build; else per-mod mixin retargets. A hold-list candidate for "just use upstream" since Lithium/C2ME compat also moves.

### bountiful-fish 1.1.1 -- medium
- CC0-1.0 (jar). Modrinth lists only 1.21.11, no repository link (sources URL is the fabric-example-mod template). Jar-in-jar: cardinal-components-base/entity 7.3.0 (CCA now has 26.1.2/26.2/26.3 builds), terraform-wood-api-v1 16.0.0 (no 26.x build found on Modrinth).
- 8 mixins: Player (4 injects at method_5773/6074/7263/7324 plus a ModifyArg and a MixinExtras @WrapOperation), WoodType (@Invoker, register a custom wood type), client: HumanoidModel.setupAnim (method_17087), EntityRenderer (method_62354 = render-state extraction), EntityRenderState (class_10017, added field/accessor). Those three client mixins ride on the render-state pipeline, which 26.x keeps reworking.
- Registers items (about 23 custom item classes), blocks, entities (5), particles, a client entity renderer/model, enchantment, damage type, trinkets tag (`trinkets/entities`, `trinkets/tags`). Data: 323 JSON (recipes 41, loot 20, advancements 22, tags ~16), 1.21.11 formats; 26.1 changed block/fluid tag usage and recipes `crafting_special_*`; check the tags for removed/renamed ids (unverified).
- Verdict medium (4 gameplay mixins + 3 render mixins + Terraform dependency). Because CC0, an actual source port to 26.3 is legal and may be cheaper than bridging if the author does not.

### furnish-furniture 31 -- use-newer-build
- License: jar and Modrinth say All Rights Reserved; the GitHub API for Wouink/furnish reports CC0-1.0 (conflict; need a human check). Source public. Branches `feature-26.2` (latest commits 2026-10-28 and 2026-06-20: fix datagen, network message registration, workbench menu) and `feature-1.21.11`; no Modrinth release past 1.21.11.
- Footprint: 104 classes; 34 block classes, 15 block-entity renderers, 12 block entities, containers/screens (3), recipe serializer (2), network (2), `reglib` internal registry, EMI plugin. Only one mixin: a client `ExampleClientMixin` on Minecraft (method_1514) which is template junk; harmless to drop. AW: `Painting.setVariant` (class_1534.method_43402).
- Exposure: BlockEntityRenderer (15 classes) is the main 26.x break (render-state extraction/submit); menus/screens (GUI render changes); network packets (registration API, which upstream's branch also fixed); recipes (`crafting_special_*` changes, recipe serializer API).
- Data: 2559 JSON, 339 loot tables, 309 advancements, 304 recipes, 117 tags, 17 `painting_variant` (data-driven since 1.21.x). Needs a smoke test for painting_variant and recipe format on 26.x; wiki lists no direct change.
- Best path: use/port from the `feature-26.2` branch (26.2 -> 26.3 is a small gap), not a 1.21.11 bridge.

## Not verified
- Anything in 26.3 data formats (wiki has no entry). Whether `legacy_single_pool_element`, `random_spread` unprefixed ids, and `fabric-resource-loader-v0` still exist in 26.3 / 26.3 Fabric API.
- Whether Fabric enforces pack.mcmeta ranges on mod-bundled packs in 26.x.
- Existence/state of `ohthetreesyoullgrow` and `terraform-wood-api` on 26.x.
- Furnish license (ARR vs CC0 conflict). Bountiful Fish source repo (not found).
- BWG's 189 gzip-defective NBT files loading on 26.x; mixin targets' presence in 26.3 (I did not have 26.3 mappings, only reasoned from names and upstream branches).
- Mixin method names are intermediary ids; I decoded some by structure, not against a mappings file.
