# libs-jobs: libraries and the jobs/embargo stack

Method: unzipped each 1.21.11 jar, read `fabric.mod.json`, mixin configs and access wideners, ran `javap` (the Semeru 21 `javap`; Temurin/JDK 25 `javap` SIGBUSes on this machine, and `~/.mms-dev/jbr` does not exist). Cross-checked Modrinth, GitHub (branch/commit diffs 1.21.11 -> 26.2 -> 26.3), the Fabric 26.1/26.2/26.3 blog posts, and the Modrinth `dependencies` of all 245 Modrinth-tracked pack mods. Nothing was run. Mixin/annotation counts are per mixin class (classes using the annotation), not per call site.

## Headline table

| Slug | License / source | Mixin classes (client) | Newest 26.x build | Verdict |
|---|---|---|---|---|
| item-restrictions | Apache-2.0, public | 15 (2) | 21.1.0 (26.2) | use-newer-build |
| arc | Apache-2.0, public | 34 (2) | 21.2.7 (26.2) | use-newer-build |
| jobsplus | Apache-2.0, public | 7 (4) | 21.2.5 (26.2, 2026-10-12) | use-newer-build |
| jobsplustools | Apache-2.0, public | 10 (3) | 21.1.0 (26.2) | use-newer-build |
| owo-lib | MIT, public | 100 (~45) | 0.13.1+26.2 | use-newer-build (bridging the 1.21.11 jar is `hard`) |
| badpackets | Apache-2.0, public | 12 (4) | fabric-0.12.2 (26.1/26.2) | use-newer-build |
| prometheus-api | CNCL v1 (non-commercial), public | 15 (5) | 1.2.5 (26.2, 2026-10-10) | use-newer-build |
| disable-items | MIT (manifest only), source not found | 3 active (6 classes) | none | easy (or rewrite) |
| cobweb | CNCL v1, public | 4 (2) | 1.4.0 (26.2); 26.3 port is on GitHub, unreleased | use-newer-build |
| origins-legacy-classes | MIT, public | 38 (5) | 1.7.2+26.2 | use-newer-build |
| wthit | CC-BY-NC-SA-4.0, public | 27 (15) | fabric-20.0.0 (26.2) | use-newer-build |
| dcintegration | MIT, public | 7 + plugin-added (~15) | 3.2.1-26.2 (beta) | use-newer-build |

Scale: none of the 12 use @Overwrite (owo has 1). @Redirect: jobsplustools 1, origins-classes 5, dcintegration 7. Everything else is @Inject, accessors, MixinExtras (WrapOperation, ModifyExpressionValue) and @Shadow.

## Key finding: the DAQEM stack is a rewrite on 26.x, not a port

- `targets.tsv` lists Arc as `None`. That is wrong: Modrinth `H3eKhxi7` has Arc 20.2.x (26.1.2) and 21.2.7 (26.2, 2026-08-03). `arc.pw.toml` pins 19.1.1 with no update tracker, which is probably why the lookup missed it.
- The 26.x line (Arc 21.x, Item Restrictions 21.x, Jobs+ 21.x, Jobs+ Tools 21.x) dropped Architectury and requires `knot` (DAQEM/Knot, GPL-3.0, id `V9WxBDCZ`), plus uilib and yamlconfig.
  - `NetworkManager.sendToPlayer` became `Knot.NETWORKING.sendToPlayer`, and `JobsPlusTools.getId` became `JobsPlusTools.API.getId`.
  - Arc's own mixin set shrank from 34 to ~10. The old hooks (Anvil/Smithing/Grindstone menus, ItemStack, LivingEntity, Furnace, FishingHook and so on) moved into Knot, which has ~77 mixin files on its 26.2 branch (~50 for Fabric).
- Arc's data format changed between 19.x and 21.x. In the 26.2 Jobs+ branch, 82 of 85 changed data JSONs differ. Examples: `arc:exp_level` -> `arc:exp_cost` with a new `comparison` field, and `min`/`max` rewards that can now be an `arc:calculate` object. Bridging the 19.x jars keeps the old format. Moving to 21.x means checking the pack's own config.
  - Today `config/arc`, `config/itemrestrictions` and `config/jobsplus` hold only the YAML (`arc-common.yaml`, `item-restrictions-common.yaml`, `jobsplus-common.yaml`, `jobsplus-client.yaml`) plus empty dirs. All job, powerup and restriction JSON comes from the jars' bundled defaults.
  - `jobsplustools` bundles 89 recipes, 89 recipe advancements and 76 restriction JSONs. Those will need re-checking against the 21.1.0 versions.
- Bridging the 19.x jars is a bad route. They need Architectury (which has 26.3 builds: 22.0.5), but they also need uilib 19.x and yamlconfig 19.x APIs, and the 22.1.0 versions of those (26.3) are the 21.x API line. The 26.2 builds plus a small 26.2 -> 26.3 bridge are the realistic target.
- Gap 26.2 -> 26.3 is small for the libs that already ported. Evidence from their port commits:
  - UILib `feat: port to 26.3` touches 9 files. The only code change is `GLFW.GLFW_KEY_*` -> `InputConstants.KEY_*` and `InputConstants.Type.KEYSYM` -> `KEYBOARD`. It targets Fabric API 0.162.0 and Loader 0.19.5.
  - YamlConfig 22.1.0 and Architectury 22.0.5 are published for 26.3. Knot has no 26.3 build or branch yet (newest 21.2.10 / 26.2). That is the choke point for the whole jobs stack.
  - Cobweb's 26.3 port (11 files):
    - `Pack.ResourcesSupplier.openPrimary/openFull` -> `openMetadata` (returns `PackMetadataResources`) / `openResources` (returns `Stream<PackResources>`).
    - `Holder` is now sealed; cobweb's AW `transitive-extendable class Holder` was removed and its registry entries had to be realigned to native 26.3 holders.
  - Architectury's 26.3 port: `LootEvent` listener param `HolderLookup.Provider` -> `HolderGetter.Provider`; `DyeColorHooks` removed; a `MenuRegistry` and `ItemEntity` mixin rework.

## Dependency map (from fabric.mod.json plus Modrinth deps of all 245 tracked pack mods)

```
fabric-loader / fabric-api
 ├─ knot (26.x only) ── arc ─┬─ item-restrictions ── jobsplus ── mms-jobs (pack-owned, not on Modrinth)
 │     (uilib, yamlconfig) ──┘            └─ jobsplustools (knot only) ── mms-jobs mixin JobsToolsMobEquipmentMixin
 ├─ owo-lib ── expanded-{armor,axe,bow,crossbow,mace,trident,weapon}-enchanting (7), water-resistance-potion (hwr)
 ├─ badpackets ── wthit ── (oh-the-biomes-weve-gone: optional)
 ├─ cobweb ── prometheus-api ── soul-fire-d
 ├─ origins-legacy (has a 26.3 build, 1.12.20+26.3) ── origins-legacy-classes ── mms-origins (pack-owned; has mixin config mms_origins.originsclasses.mixins.json)
 ├─ architectury-api (26.3: 22.0.5) ── leawind-third-person only, once the pack drops the 19.x jobs jars
 ├─ disablemod: no mod depends on it (config/disablemod.json, 3.8 KB list of disabled item ids = the embargo)
 └─ dcintegration (server-only): nothing depends on it
```

If a lib cannot be bridged, these fall with it:
- **Knot**: arc, item-restrictions, jobsplus, jobsplustools, mms-jobs (the whole jobs/embargo stack apart from disablemod).
- **owo-lib**: 7 Expanded Enchanting mods plus Water Resistance Potion. Of these, owo-lib is the biggest risk and by far the heaviest to bridge.
- **cobweb** (small, 4 mixins): prometheus-api, soul-fire-d.
- **badpackets** (12 mixins, networking): wthit.
- **origins-legacy-classes**: mms-origins' classes module.
- Pack-owned dependents (`mms-jobs`, `mms-origins`) are not in `jars/`. `mms-jobs` mixins into Arc/Jobs+ internals (ActionDataCooldownMixin, JobXpCooldownMixin, JobLevelUpMessageMixin, StockJobsFilterMixin, JobsToolsMobEquipmentMixin) and declares `minecraft ~1.21.11`. It must be rebuilt against the 21.x API; the source is yours.

## Per-mod notes

**item-restrictions** (19.1.0). Registers no blocks or items, only a packet plus restriction JSON. 15 mixins: PlayerList, Player, Anvil/Smithing/Crafting/Enchantment/Grindstone/Brewing/Furnace menus and block entities, and client Screen render plus ClientLanguage. Exposure: `Screen.render(GuiGraphics...)` (class_437.method_25394) -> 26.x `extractRenderState(GuiGraphicsExtractor...)` (confirmed in the 26.2 source), `ValueInput/ValueOutput` save/load (class_11368/11372), `CraftingMenu.slotChangedCraftingGrid`. Depends on architectury, yamlconfig, arc, `fabric` (id). Use 21.1.0 (needs arc, knot, ui-lib, yaml-config). Uproot: nothing unless bridging 26.2 -> 26.3 with Knot.

**arc** (19.1.1). The registry of actions/conditions/rewards for the jobs stack (`arc:on_craft_item`, `arc:items`, ...). 34 mixins (8 Fabric, 24 common, 2 client) into Anvil/Smithing/Grindstone menus, ItemStack.hurt, Player, LivingEntity, FishingHook, Consumable, BucketItem/Axe/Hoe, `BlockStateBase.useItemOn` (class_4971 method_55780), Ingredient, AbstractCookingRecipe, ClientLanguage. 7 mixins use `LocalCapture`. 169 distinct intermediary classes referenced. Uses Architectury events (Block/Entity/Interaction/Player/Tick/CommandRegistration events, NetworkManager, ReloadListenerRegistry, Platform) and Fabric `ArgumentTypeRegistry`. Client: a few scroll/tab widgets use `blaze3d`. Pinned by pack at 19.1.1 because the 19.1.2 release was only a dev jar. Use 21.2.7 (+ knot, uilib, yamlconfig).

**jobsplus** (19.1.2). 202 classes, 310 data files (jobs, powerups, arc action JSON) and 78 assets. 7 mixins: ServerPlayer (tick/save/load/die; uses ValueInput/Output), ServerLevel, PrimaryLevelData (ctor + `Dynamic` load), plus client ClientLanguage, ClientPacketListener, LocalPlayer, AbstractScrollArea accessor. 29 classes use GuiGraphics (toasts, widgets, HUD). Uses Architectury ClientGuiEvent.RenderHud, ClientRawInputEvent (key handling) and ClientTooltipEvent, which are tied to GLFW -> SDL and the GuiGraphics -> GuiGraphicsExtractor split. Use 21.2.5 (published 2026-10-12, so Mojang-26.2 current).

**jobsplustools** (19.1.3). Registers items and data components (mode/experience/potion-storage), 89 recipes, 408 assets (item definitions, models). 10 mixins: AbstractArrow, ArmorStand (`interactAt` -> `interact` in 26.2), FishingHook (1 @Redirect), ItemStack (tooltip), Player, SmithingTransformRecipe (`assemble(input, HolderLookup.Provider)` lost its provider arg in 26.x), BlockBehaviour (Fabric), and client LevelRenderer (block outline render: `SubmitNodeCollector`, `BlockOutlineRenderState` in 26.2), `ItemTintSources` and `RangeSelectItemModelProperties` (ID_MAPPER registration). AW removed in 26.2. Highest render exposure of the four. Use 21.1.0 (knot only).

**owo-lib** (0.13.0+1.21.11). MIT. 100 mixins, 1068 classes, a custom UI renderer (braid package: `OwoUIPipelines`, `OwoUIGraphics`, `NinePatchTexture`, GuiRenderer/KeyboardHandler/ScreenMixins), 8 classes using `org.lwjgl.glfw` and 6 using `lwjgl.opengl`/`blaze3d.opengl` (CursorAdapter, BraidWindow, AppState). 53 classes use `GuiGraphics`; 11 use the render-state classes. AW covers `Screen.addRenderableWidget`, `CreativeModeTab` ctor, `GuiGraphics` tooltip method, `Checkbox` ctor. Fabric API: SpecialGuiElementRegistry, TooltipComponentCallback, FabricCreativeInventoryScreen, ClientConfigurationNetworking. JiJ: endec, gson, jankson, kdl4j, netty (all pure libs, no MC refs). Default branch is `braid-ui` (a UI rewrite in progress). Bridging the 1.21.11 jar would be `hard`; use 0.13.1+26.2 and only bridge 26.2 -> 26.3 (GLFW -> SDL input constants, mouse button numbering, TextInputManager focus notifications for text widgets, GuiGraphics changes). Its 26.2 -> 26.3 gap is unverified; no 26.3 branch exists and no 26.3 issue was found.

**badpackets** (0.11.2). Apache-2.0, master branch only. 12 mixins into the networking layer: `Connection`, `ClientboundCustomPayloadPacket` / `ServerboundCustomPayloadPacket` static codec init, `CustomPacketPayload$1` (the codec lambda holder), `PlayerList.placeNewPlayer`, `ServerConfigurationPacketListenerImpl`, `ServerGamePacketListenerImpl`, client `ClientCommonPacketListenerImpl`/`ClientConfigurationPacketListenerImpl`/`ClientPacketListener`. Targets `StreamCodec` internals (class_9139 `method_56432`) that are fragile. Use fabric-0.12.2 (26.1/26.2; the last commit is 2026-04-05; open issue #24 "26.3, please"). Small, so the best candidate for a fork or per-mod port if upstream stalls.

**prometheus-api** (1.2.5). CNCL v1, depends on cobweb ^1.4.0. 15 mixins: Entity, AbstractArrow, Zombie, SmallFireball, AbstractHurtingProjectile, BaseFireBlock, Campfire/Lantern/Torch, FireChargeItem, plus client EntityRenderer, EntityRenderState, **FlameFeatureRenderer**, **ScreenEffectRenderer** (fire overlay). Uses Fabric attachments, networking, `ResourceLoader`, ParticleFactoryRegistry, BlockRenderLayerMap. Its 26.2 port changed the two renderers (`Material`/`MaterialSet`/`PoseStack`/`MultiBufferSource` -> `SpriteId`/`SpriteGetter`/`SubmitNodeCollector`) and dropped `BaseFireBlockMixin` (Fabric). Use 1.2.5 for 26.2.

**disable-items** (disablemod 1.0.0, MIT per manifest, `com.example` package, CurseForge-only; no source repo found). 20 classes, 3 active mixins (CraftingMenu `slotChangedCraftingGrid`, ResultSlot `onTake`, MerchantResultSlot `onTake`/`method_7671`). `MerchantEntityMixin`, `VillagerEntityMixin` and `PreparedRecipesAccessor` are in the jar but not in the mixin config. Fabric API use: `CommandRegistrationCallback` only. Exposure: depends on `fabric` (removed id) and `minecraft 1.21.11` (exact); `ItemStack` handling (`ItemStackTemplate` in 26.1); recipes now reloadable registry in 26.3. Footprint is tiny, so easy; if you want it in the repo, rewrite it as a small own mod (MIT). Config is `config/disablemod.json`.

**cobweb** (1.4.0). CNCL v1. 4 mixins: `PackRepository`, `ServerPacksSource`, client `CreateWorldScreen`, `Minecraft`. AW: `PackRepository.sources`, `BlockEntityType$BlockEntitySupplier`, `BlockEntityType.<init>`, `Holder` (transitive-extendable). It is a registry/pack-registration helper; on 26.3 the exposure is `Holder` becoming sealed and `Pack.ResourcesSupplier` changing (see above). Source of the 26.3 port is public on its `26.3` branch (commits 2026-10-15); the 26.3 build is not yet on Modrinth. Easy to wait for or build from source, mind the license for redistribution.

**origins-legacy-classes** (1.7.2). MIT. 38 mixins, 50 data files, 20 assets. Concrete 26.2 changes seen in the port (16 files): `MobEffect.isInstantenous` -> `isInstantaneous`; `BlockEntityType.SMOKER` -> `BlockEntityTypes.SMOKER`; lambda targets `method_17410/17411` -> `lambda$clickMenuButton$0` / `lambda$slotsChanged$0`; `RepairItemRecipe.assemble(input, HolderLookup.Provider)` -> `assemble(input)`; `PayloadTypeRegistry.playS2C()` -> `clientboundPlay()`. 5 @Redirect, 3 @ModifyConstant. Depends on `origins-legacy` (has a 26.3 build, 1.12.20+26.3). Use 1.7.2+26.2; MIT, so porting the last gap by hand is straightforward.

**wthit** (18.2.2). CC-BY-NC-SA-4.0. 27 mixins; biggest exposure is client: GuiGraphics, Keyboard/MouseHandler, KeyBindsList, Options screen. 26.2 port changes: `GuiGraphicsAccess` -> `GuiGraphicsExtractorAccess` (plus a `GuiGraphicsExtractor$ScissorStack` mixin), `GuiRenderState` moved to `renderer.state.gui`, `ReloadableServerResources.<init>` gained a `List` param (`newComponents`), `updateStaticRegistryTags` -> `updateComponentsAndStaticRegistryTags`. Fabric API use is broad (ClientTickEvents, ItemTooltipCallback, HudElementRegistry, BlockApiLookup, transfer API, ConventionalItemTags v2, ResourceLoader). Optional entrypoints for EMI/JEI/REI/ModMenu. Use fabric-20.0.0; open issue #375 for 26.3. Depends on `fabric` id and badpackets.

**dcintegration** (3.2.0, server-only). MIT. 7 mixins in `dcintegration.mixins.json` plus mixins that `StyledChatCheckerPlugin` (an `IMixinConfigPlugin`) adds from `fabric-like` (Commands, PlayerList, ServerGamePacketListenerImpl, PlayerAdvancements, TamableAnimal, Say/Msg/Emote/TeamMsg commands, signed-chat classes `PlayerChatMessage`/`OutgoingChatMessage`/`MessageArgument`, MinecraftServer). 7 @Redirect, 5 @ModifyArg. 28 mixin classes in all. The 1.21.11 -> 26.2 source diff is tiny (one `getItemHolder().getRegisteredName()` line plus build files), so it behaves as `easy` if bridged. The jar nests five JiJ jars with their own fabric.mod.json: `dcintegration.common-3.2.0` (9859 files, Java side shaded), `fabric-permissions-api` (two versions), `placeholder-api 2.8.2+1.21.10` (130 classes), `player-data-api 0.9.0+1.21.9` (has its own mixin config), `predicate-api 0.7.0`. Uproot must recurse into JiJ jars. Use 3.2.1-26.2 (marked beta on Modrinth).

## Cross-mod patterns uproot should handle generically

1. **Mapping is not just intermediary -> official.** Official names changed between 1.21.11 and 26.x, so a 1.21.11-official -> 26.3-official rename table is also needed. Seen so far: `GuiGraphics` -> `GuiGraphicsExtractor` (and `render` -> `extractRenderState`); `GuiRenderState` moved package; `BlockEntityType.X` constants -> `BlockEntityTypes.X`; `isInstantenous` -> `isInstantaneous`; `ReloadableServerResources.updateStaticRegistryTags` -> `updateComponentsAndStaticRegistryTags`; `Recipe.assemble` lost its `HolderLookup.Provider` arg; `Pack.ResourcesSupplier` methods; `ArmorStand.interactAt` -> `interact`.
2. **Lambda targets.** Mixins that target synthetic lambdas by intermediary name (`method_17410`, `method_17411`) must map to `lambda$name$N` and the numbering can differ (origins-classes, jobsplus, owo and others have 5-28 lambda references).
3. **Mixin config plugins and JiJ.** dcintegration uses a config plugin that registers mixins dynamically, and ships nested jars with their own mixins/AW. owo-lib and dcintegration both nest libraries. Handle both.
4. **No refmaps.** All 12 jars are already remapped to intermediary in the annotation strings and ship no refmap (cobweb, prometheus and dcintegration name one in the config but the file is not in the jar). Rewriting annotation strings directly is enough, no refmap logic needed. Access wideners are `v2 intermediary` and need the same rewrite.
5. **Fabric API changes hitting these jars.** `fabric` mod id is gone (26.1): itemrestrictions, arc, jobsplus, jobsplustools, wthit, dcintegration and disablemod depend on `fabric`, so rewrite `fabric` -> `fabric-api` in `fabric.mod.json`. `PayloadTypeRegistry.playS2C()/playC2S()` -> `clientboundPlay()/serverboundPlay()`. `HudRenderCallback` was removed in 26.1. 26.3 also removed `CompostingChanceRegistry`, `FuelRegistry`, `StrippableBlockRegistry`, `TillableBlockRegistry`, `FlattenableBlockRegistry`, `FabricPotionBrewingBuilder`. None of the 12 jars were found using the removed registries. Also a hard exact `minecraft` range (`1.21.11`, `~1.21.11`) must be widened.
6. **Input.** 26.3 swaps GLFW for SDL: GLFW constants and mouse button numbers change, and custom text widgets must notify `TextInputManager`. Affects owo-lib (8 GLFW classes), wthit (Keyboard/Mouse mixins), jobsplus (`ClientRawInputEvent`).
7. **Items.** `new ItemStack(...)` cannot be called before world load in 26.1+ (`ItemStackTemplate`). 11 item-restrictions, 84 arc, 27 jobsplustools, 50 wthit classes touch `ItemStack`; any static-init construction is a risk.
8. **Reloadable registries (26.3).** Recipes and advancements now load via the dynamic registry path. Mixins into `RecipeManager`/`ReloadableServerResources`/`PreparedRecipes` (wthit, arc, disablemod, origins-classes) will need per-mod retargets.
9. **Java and mixin.** Mods target Java 21 and 26.x needs Java 25; mixin configs say `JAVA_21`.

## Could not verify

- Whether the 26.2 builds of owo-lib, wthit, badpackets, prometheus, origins-legacy-classes and dcintegration run unmodified on 26.3. Only UILib, YamlConfig, Architectury and Cobweb have visible 26.3 ports. Knot has none.
- Whether the Arc/Jobs+/Item Restrictions 21.x data formats can read the 19.x-era behaviour exactly. The pack's own config dirs are empty of JSON but the jar defaults changed.
- The source and license of disablemod (CurseForge-only; the repo search found none). The 20-class jar is simple enough to read, but its behavior beyond the commands was not fully traced.
- 26.2 -> 26.3 specifics for Mojang render/GuiGraphicsExtractor/SDL beyond what the Fabric blog and the ported libs' commits show. No NeoForged primer was read.
- Modrinth version dates and "beta" flags are as returned by the API on 2026-10-29. CNCL v1 (Crystal-Nest) redistribution terms were not read in full.
