# content-b findings (mms-uproot assessment)

Method: unzipped jars, read fabric.mod.json, mixin configs, refmaps, pack.mcmeta, data JSON; `javap -v` for MC refs.
No web lookups done (see "Not verified"). Class/method names below are intermediary ids as found; mojmap names from memory.

| slug | newest build | license | src public | kind | mixins | verdict |
|---|---|---|---|---|---|---|
| happy-ghast-but-fast-(3x-speed) | 26.2-snap | MIT | no (datapack) | datapack, 1 tick fn | 0 | easy (data only; use 26.2 build) |
| hurl | 26.2 | LGPL-3.0 | yes (yyyasso/Hurl-Fabric-1.21) | content mod (items, entity, enchant, damage type) | 1 | use-newer-build |
| invisible-armor-recipe | 26.2 | MIT | no | datapack, 56 recipes | 0 | easy (data only; use 26.2 build) |
| training-dummy | 26.2 | CC-BY-SA-4.0 | yes (Silentcat12/Training-Dummy) | datapack | 0 | easy (use 26.2 build) |
| trigger-happy-ghast | 26.2 | All Rights Reserved | yes, but ARR (UY-scuti73/Trigger_Happy_Ghast-Mod) | server logic + client config screen | 2 cfgs, 0 listed mixins (client list empty, common list empty) | use-newer-build |
| water-resistance-potion | 26.2 | LGPL-3.0 | yes (codeberg Alliegaytor) | potion/loot content | 4 (1 client) | use-newer-build |
| soul-fire-d | 26.2 | CNCLv1 | yes (crystal-nest) | content (fire types, loot, recipes) | 0 | use-newer-build |
| nametag-recipe-backport | 1.21.9 | ARR | no | datapack, 1 recipe + 1 tag | 0 | not-worth-it (vanilla 26.1+ has it) |
| ante | 1.21.9 | "GNU" (unclear) | example-mod link only | 2 mixins | 2 (1 client) | easy |
| arrow-entity-loot-drop | 1.21.11 | GPL-3.0 | yes (Pepperoni-Jabroni) | 1 mixin | 1 | easy/medium |
| bura | 1.21.11 | CC0-1.0 | source field points to fabric-example-mod (bogus) | perf throttles | 11+ (7 client) | hard |

## Datapack / recipe-only (no bytecode bridging, data conversion at most)
- happy-ghast-but-fast: 1 function `execute as @e[type=happy_ghast] ... attribute @s flying_speed base set 0.15`. pack_format 55 / max_format 107.1 already declared. No conversion if 26.3 pack format is within range; else just bump. Needs uproot to do nothing but possibly pack-format override. The 26.2 jar already exists.
- invisible-armor-recipe: 56 `crafting_transmute` recipes, results set `equippable.asset_id` (structure_void). Uses already-modern 1.21.11 recipe/component syntax; pack range 57-94 so a pack-format override at most. Equipment assets (`asset_id`) still resolve to registry-keyed equipment models in 26.x; structure_void model must exist as a resource (not bundled: check). 26.2 build exists.
- training-dummy: functions, advancements, predicates, enchantment, recipe, plus assets/items + entity model. Uses armor_stand + slime NoAI with `attributes:[{id:scale}]` and `item_model` component; the NBT summon syntax is the part most likely to change between versions (26.x kept SNBT components but verify). Pack range min 87 max 999. Data conversion only; 26.2 build exists.
- nametag-recipe-backport: description says "Nametag recipe from 26.1". Vanilla 26.1+ ships the name_tag recipe, so the pack is REDUNDANT on 26.3 (and a duplicate recipe id minecraft:name_tag would override vanilla). Drop it. Pack max_format 26 means it would be flagged incompatible anyway.
- soul-fire-d has `class` code but 0 mixins; content via Crystal Nest libs (cobweb, prometheus). Its data is advancement + recipe only; loot built at runtime with class_55 / class_117 etc (loot table builders), not datapack JSON.

## Per mod notes
**hurl**: 1 mixin `MaceItemsMixin` -> `@Inject` into `Items.registerItem(String, Function, Item.Properties)` (method_63750) targeting an `ResourceKey.create(Registries.ITEM, ...)` call; registers items, an entity (class_1299/class_1297 refs, thrown/projectile, packet class_2596, entity data class_2940/2945), enchantment, damage type, models. Footprint touches `Items`, `Item$Properties`, `EntityType$Builder`, `SynchedEntityData`, client render state (class_10017, class_11659 submit node storage, class_4587 PoseStack), `Entity` renderer. High exposure to render pipeline and item/registration changes. LGPL source + 26.2 build => take the 26.2 build and bridge 26.2->26.3.

**trigger-happy-ghast**: ARR, so cannot fork; uses Ghast fire (class_1674 fireball), `Items`, `Player`, `Level`, `ServerPlayer`; client uses Screen/Button/EditBox (modmenu config screen, class_437/4185/342/357, GuiGraphics class_332). The mixin configs list 0 mixins, so the risk is only API calls: `Screen`/`AbstractWidget` ctor and render signatures, `InteractionResult` (class_1269$class_9859/9860 Success/Pass), GuiGraphics changes. Fabric depends: fabric-api `*`, minecraft pinned `1.21.11` (version-range override needed). Bridge from 26.2 build.

**water-resistance-potion**: 4 mixins: `LivingEntity.baseTick`(method_6007 aiStep) with `isSleeping` at; `LivingEntity.hurtServer` (method_64397) with `isSleeping` at (Origins variant, optional); `ConsumableComponent`(class_10124).`onConsume` (method_62847) with `getEffect` at; client `WaterFogModifierMixin` -> class_11403 (a fog modifier, new in 1.21.11 `FogRenderer`-environment fog) `applyFogColor`-style (method_42591) with `Camera.getEntity`. Fog rendering class was rewritten between 1.21.11 and 26.x (fog UBO, `FogRenderer`), so the client mixin is the high-risk one. Also owo-lib dependency (owo 0.13 has a 26.x build? unverified). LGPL source: bridge from 26.2 or port.

**soul-fire-d**: no mixins but uses Cobweb + Prometheus (both content-a/other group libs, must also be bridged or have builds). Uses loot condition builders (class_5341, class_219 LootItemRandomChance, class_55/class_52 LootTable builders) at datagen/runtime, `CreativeModeTab` (class_1761, class_7706), `PackSource` (class_3288) for built-in pack registration. Fabric deps: minecraft `~1.21.11`. 26.2 build exists; CNCL license -> do not fork, load via 26.2 build.

**ante**: AnvilMenuMixin `@ModifyConstant` in `AnvilMenu.createResult` (method_24928 per refmap, 40 -> big) and AnvilScreenMixin `@ModifyConstant` in `AnvilScreen.renderLabels`(method_2388). Both ModifyConstant on literal 40. Needs: remap, then verify the constant still exists. `AnvilScreen.renderLabels` was reworked in 1.21.11/26 GUI (check). Uses refmap: the mappings are intermediary, so uproot must remap the refmap strings, not just the class.

**arrow-entity-loot-drop**: 1 mixin on `LivingEntity.die`? (method_6074 = `die`... `Inject` with `DamageSource.getEntity` (method_5526) then `AbstractArrow.getPickupItemStackOrigin`/`getDefaultPickupItem`-style (method_54759)). Method desc `(ServerLevel, DamageSource, float)` hints `hurtServer`/`dropAllDeathLoot`. Method sig `hurtServer(ServerLevel,DamageSource,float)` exists since 1.21.2. Server only, tiny. Verify `AbstractArrow.pickup` and `getPickupItem` shape in 26.x (arrow item stack logic was refactored with the new `weapon` field in 1.21). Easy to medium.

**bura**: CC0, but unusual: written for 1.21.11 (mojmap-oriented code compiled to intermediary, `bura/com`). 55 classes, 11+ mixins hit the hottest vanilla code: `ServerChunkCache.tick` (class_3215, with ChunkMap.tick ModifyArg), `LivingEntity.aiStep`(method_6023), `SectionRenderDispatcher$RenderSection` (class_846$class_851, @Redirect on rebuild), `SectionRenderDispatcher` upload, `ParticleEngine.add` (class_702), `Minecraft.tick`, `BlockEntityRenderDispatcher.submit` (class_824; uses class_11659 SubmitNodeCollector, class_12075 CameraRenderState), `TextureAtlas.cycleAnimationFrames` (class_1059), debug overlay (class_340), `EntityRenderer` (class_898/10017). These are exactly the render/chunk-pipeline targets rewritten in 26.x (SectionRenderDispatcher, submit-node renderers, chunk loading). Hard; the mod is a perf toy with 0.2.0 version, no 26.x build -> drop or rewrite; replace with Noisium/ImmediatelyFast-type modern alternatives if wanted.

**nametag-recipe-backport**: see above, drop.

## Cross-mod patterns for uproot
1. Pack formats: several datapacks declare ranges ending at 94/107.1/999/26; uproot should inject a pack format override (or ignore `max_format`) rather than editing jars. Check 26.3's exact format (26.1+ use `major.minor`, e.g. 107.1 style).
2. Mod-version pins: `minecraft: ~1.21.x` / `1.21.11` in fabric.mod.json for hurl, trigger-happy, soul-fire-d, bura, arrow. Uproot needs a depends override, not just class rewrite.
3. Refmaps are intermediary; remap the refmap contents and the `method`/`at` strings too (ante, others).
4. `InteractionResult` (class_1269$class_9859 etc.), registry-key helpers (`Items.registerItem`), `LivingEntity.hurtServer`/`aiStep` shims are shared hot spots.
5. Five of eleven have a 26.2 jar: always prefer it to the 1.21.11 jar.
6. Data/runtime split: 4 of 11 are pure datapacks; handle as datapack mounting, not bridging.

## Not verified
- No Modrinth/GitHub lookups done: 26.2 builds' porting diffs and existence of 26.3 builds not checked; targets.tsv version lists were trusted.
- Mojmap names above are from memory, not decompiled; javap output is intermediary only. Quoted method meanings (hurtServer, aiStep, die, renderLabels) are best guesses.
- Whether the AnvilMenu/AnvilScreen constant 40 and the fog class (class_11403) still exist in 26.3.
- 26.3 pack_format value; whether `structure_void` equipment asset exists in vanilla 26.x.
- Whether owo-lib, Cobweb and Prometheus have 26.x builds (dependencies of water-resistance and soul-fire-d).
