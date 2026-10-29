# Client group: uproot assessment

Method: unzipped the 1.21.11 jars, read fabric.mod.json and mixin configs, javap on mixin and mod classes, Modrinth API lookups (queried 2026-10-29). Mixin counts below are from the mixin config lists. A grep of all classes found zero @Overwrite and zero @Redirect in every mod of this group. Chloride and Leawind use MixinExtras (@WrapOperation); the rest use plain @Inject / @Accessor / @Invoker / @Shadow.

Key fact: Modrinth has no 26.3 build for any of the 9 slugs. But one new thing turned up: **Animatica Refabricated** (a different, maintained fork) has a 26.3 build. See the animatica row.

## Summary table

| Slug | License / source | Mixins | Newest build | Verdict |
|---|---|---|---|---|
| animatica-continuation | LGPL-3.0-or-later, public | 3 client | 1.21.11 | **use-newer-build**: swap to Animatica Refabricated 0.7.0 (has 26.3) |
| chloride | All-Rights-Reserved, GitHub repo public (SrRapero720/chloride) but license is ARR | ~36 client, plugin | 26.2 (1.8.1) | **use-newer-build** (26.2 jar); bridging 1.21.11 is hard |
| fabrishot | MIT, public | 3 client | 26.1.2 (1.17.0) | **use-newer-build** |
| leawind-third-person | MIT, public | 9 client (+3 platform stubs) | 26.2 (3.0.3-beta) | **use-newer-build** (needs perspective-api 26.2 too) |
| perspective-api | MIT, public | ~14 client (+platform stubs) | 26.2 (1.5.0-beta) | **use-newer-build** |
| locator-border | MIT, public | 3 client | 26.1.2 (1.2.9) | **use-newer-build** |
| mixintrace-reloaded | MIT, public (comp500/mixintrace) | 2 | 26.1.1 (1.1.0) | **use-newer-build** (trivial) |
| nocreativedrift | GPLv3 jar / LGPL-3.0 Modrinth tag (inconsistent), public | 0 | 1.21.11 (6.0.0.2) | **not-worth-it**: replace with `please-stop` (26.3 build) |
| paginatedadvancements | MIT, public | 2 client + AW | 26.2 (2.8.0) | **use-newer-build** |

None of these register blocks, items, entities, worldgen or datapack JSON. All are client-only. Resource side is small: lang files, textures, no pack-format-sensitive data (checked asset dirs: json + png only).

## Cross-mod points

- Every mod here has a 26.1/26.2 source-available (MIT/LGPL) build or a 26.3 replacement. Bridging these 1.21.11 jars is the worst option; fetching the newer builds is cheaper and safer.
- Uproot would still have to bridge their **26.1/26.2 -> 26.3** gap, which is small. A generic need: allow a mod to declare `minecraft: ~26.2` and let uproot override the version check (loader `depends` rewrite) for these.
- Sodium has a 26.3 build (0.9.3-beta.1) and Iris has 1.11.7+26.3, so the "26.x Sodium/Iris breaks it" risk is not blanket. Real risk is Chloride, which hooks Sodium's config API and was built for Sodium 0.8.x (`sodium >= 0.8.13`, entrypoint `sodium:config_api_user`). Whether 0.9.x kept that API is unverified.
- Chloride is flagged `incompatible` with Smooth Skies on Modrinth.
- Remaining generic work if bridging is ever used: intermediary -> official names for `class_310/Minecraft`, `class_757/GameRenderer`, `class_332/GuiGraphics`, `class_2960/Identifier` (note the Identifier rename: animatica mixes into it), plus `class_11908` (KeyEvent) and `class_11910` (MouseButtonEvent) input records, which are already post-1.21.9.

## Per-mod notes

### animatica-continuation (0.6+1.21.11)
- Mixins: `TextureManagerMixin` (TextureManager.getTexture, method_4619), `IdentifierMixin` (Identifier ctor, validity check method_29184), `VideoOptionsScreenMixin` (VideoSettingsScreen, class_446; adds an OptionInstance to OptionsList class_353).
- Render exposure is heavy and in non-mixin code: `NativeImage` (class_1011) pixel ops, `DynamicTexture(Supplier, NativeImage)` (class_1043), `TextureManager.register` (method_4616), `RenderSystem.queueFencedTask`/`isOnRenderThread`, `ResourceManager.listResources`. In the 26.x texture/GPU rewrite these are exactly the areas that moved. Hard to bridge, and 26.x Sodium interplay matters for animated sprites.
- Don't bridge. Animatica Refabricated (LGPL-3.0-only, github.com/coredex-source/AnimaticaRefabricated) has 0.7.0+beta.4 for 26.1, 26.2, **26.3** (published 2026-10-27). Different mod id, so pack config and mod id references need updating. It is beta.
- Verdict: use-newer-build (replace). Uproot needs nothing.

### chloride (1.8.1, 1.21.11 build)
- ~36 client mixins (config list), with a MixinPlugin. Targets: LevelRenderer (class_761), GameRenderer (class_757), FogRenderer/fog classes (class_12202, class_11398, class_12197 new fog types), LightTexture (class_765) and darkness mixins, SkyRenderer/CloudRenderer (class_9955 clouds), ParticleEngine (class_702), EntityRenderDispatcher (class_898), BlockEntityRenderDispatcher (class_824), Window (class_1041), KeyboardHandler, MouseHandler, Font (class_327 glyph shadow, method_71795/71796), Gui overlay, ClientLevel, DimensionType, LeavesBlock, plus JEI/REI/EMI overlay mixins and Sodium config API mixins (`SodiumConfigBuilderMixin`, `VideoSettingsScreenMixin`).
- Uses @Shadow/@Accessor-style targets on `method_72917`, `method_74348` (very new mappings), i.e. already tied to the 1.21.11 render-state extraction design. 26.x rendering changes (fog, sky, light texture, entity render state) will break most of these. Sodium dependency adds another unknown.
- ARR license: do not redistribute; but the 26.2 build is a normal Modrinth download, so use it. Repo is public but license is All Rights Reserved, so porting/forking is not allowed.
- Verdict: use-newer-build (1.8.1-FABRIC-26.2). Uproot needs: a 26.2 -> 26.3 retarget layer for fog/sky/light mixins, plus Sodium config API compatibility check. Highest risk mod in this group.

### fabrishot (1.16.4)
- 3 mixins: `MinecraftClientMixin` (injects around GameRenderer.render, method_3192, and Minecraft.method_1523), `KeyboardMixin` (KeyboardHandler keyPress with KeyEvent class_11908; calls `ScreenshotRecorder.saveScreenshot` method_1659 with RenderTarget class_276), `WindowMixin` (Window width/height getters method_4480/4486/4502/4507, overridden to render oversized frames).
- Render exposure: RenderTarget/framebuffer sizing, NativeImage, `Window` size overrides. 26.x Window/RenderTarget API shifts make this fragile. 26.1 build (1.17.0, MIT) exists.
- Verdict: use-newer-build. Uproot needs a 26.1 -> 26.3 check of Window and screenshot signatures only.

### leawind-third-person (3.0.3-beta) and perspective-api (1.5.0-beta)
- Same author, MIT; leawind depends on perspective_api >= 1.5.0-beta.
- perspective-api (~14 mixins): `Camera` (class_4184: fields field_18711-18718, setup method_19321, new Frustum/near plane via `Camera$NearPlane` class_6355), `GameRenderer` (getFov method_3196, `method_22973` getProjectionMatrix, `RenderSystem.setProjectionMatrix(GpuBufferSlice, ProjectionType class_10366)`, `method_76295`), `Frustum` (class_4604, field_40824), `MouseHandler` (class_312 with MouseButtonEvent class_11910), `Gui` (class_329 crosshair), `Minecraft` (method_1574, method_1508).
- leawind (9 mixins): `GameRenderer` (method_3192 render, method_3190), `Minecraft` (method_1536/1583/1590 attack/use/pick), `LocalPlayer`/`MovementInput` (class_744, class_241 forward/left impulses), `Entity`, `Gui`, `MouseHandler`, and two sound mixins (`SoundInstance` position, class_1106/1109). Mostly input and camera, not shaders.
- Camera + projection internals are the most-changed render area (RenderSystem projection setters, Camera setup/state). The authors already ship 26.1 and 26.2 builds.
- Verdict: use-newer-build for both. Uproot needs: a 26.2 -> 26.3 retarget of Camera/GameRenderer injection points (small, if any).

### locator-border (1.2.9)
- 3 mixins: `GuiMixin`, `LocatorBarRendererMixin` (LocatorBarRenderer class_11264 draws waypoints; uses `WaypointStyle`/Icons class_11200, `TrackedWaypoint` class_11728, `Camera`, `PlayerInfo` class_640), `ClientWaypointManagerMixin`. Heavy GuiGraphics (class_332) drawing and the new 1.21.6+ waypoint classes.
- GuiGraphics rendering APIs changed in 26.x; 26.1 build is MIT, source public.
- Verdict: use-newer-build (26.1 only, 26.1 -> 26.3 gap is larger than 26.2). If it fails to bridge, porting from source is cheap (3 mixins).

### mixintrace-reloaded (1.1.0)
- 2 mixins: `MixinCrashReport` (CrashReport class_128, field_1088) and `MixinCrashReportSection` (CrashReportCategory class_129, field_1097). No rendering.
- Same version number 1.1.0 exists for 26.1/26.1.1. There is also an independent port (`mixintrace-unofficial-tt-port`, MIT, 26.1 only).
- Verdict: use-newer-build; if not, easy remap. Nothing else needed.

### nocreativedrift (6.0.0.2)
- 0 mixins; pure API use: Fabric `ClientTickEvents.END_CLIENT_TICK`, `HudElementRegistry.attachElementAfter` + `VanillaHudElements.HOTBAR`, `KeyBindingHelper.registerKeyBinding`, `KeyMapping(String,int,KeyMapping$Category)` (class_304$class_11900), `LocalPlayer.input` (class_744; `PlayerInput` record class_10185), `getDeltaMovement/setDeltaMovement`. Also nests MidnightLib 1.9.2 (`META-INF/jars`), which would need its own 26.3 build (unverified).
- Newest build is 1.21.11 (Modrinth lists up to 1.21.11; targets.tsv says 1.21.9, stale). No 26.x build.
- Replacement: `please-stop` (MIT, github.com/peetsamods/please-stop), 0.1.0-beta.5+26.3 fabric, client-only, "removes creative flight drift". Beta, small author.
- Verdict: not-worth-it. Uproot would need only trivial remaps, but the nested MidnightLib makes bridging less clean than the replacement.

### paginatedadvancements (2.8.0)
- 2 client mixins: `MinecraftClientMixin` (opens the advancements screen: setScreen method_1507, `AdvancementsScreen` class_457, `ClientAdvancements` class_632) and `AdvancementWidgetAccessor` (class_456 widget fields, `AdvancementTab` class_454, `AdvancementNode` class_8781). An access widener is used. Heavy GuiGraphics use (class_332). Depends on Cloth Config.
- No datapack JSON; only lang/textures.
- 26.1 and 26.2 builds exist (MIT). 
- Verdict: use-newer-build (2.8.0+26.2). Uproot needs: 26.2 -> 26.3 retarget of AdvancementsScreen/Tab/Widget accessors if they changed, and Cloth Config for 26.3 (unverified).

## Could not verify
- Contents of the 26.1/26.2 -> 26.3 mixin target changes. I did not download or inspect the 26.x jars; the 26.x render API details above are inferred from the 1.21.11 class usage, not from 26.x sources or Mojang changelogs.
- Whether Sodium 0.9.3-beta.1 kept the config API Chloride uses.
- Whether Cloth Config and MidnightLib have 26.3 builds.
- Actual state of the GitHub repos beyond Modrinth metadata (WebFetch on GitHub returned only partial pages).
- Mixin counts for perspective-api and chloride are read from the config lists; `@Redirect`/`@Overwrite` absence from a byte grep, not full decompilation.
