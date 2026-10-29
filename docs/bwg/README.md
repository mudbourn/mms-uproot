# Oh The Biomes We've Gone: 1.21.11 → 26.3 reference map

I checked every reference in BWG 4.4.2 and CorgiLib 9.0.0.1 against the real 26.3 jars: Minecraft 26.3, Fabric API 0.165.0+26.3, TerraBlender 26.3.0.0.9, GeckoLib 5.5.7 and Oh The Trees You'll Grow 12.0.3. The references come from the constant pool, mixin annotations and access wideners. Generated 2026-10-29 by [`../../tools/uproot-map/uproot_map.py`](../../tools/uproot-map/uproot_map.py). There is one row per reference in each CSV, and [SUMMARY.md](SUMMARY.md) lists every non-OK reference.

To rerun it, run the fetch scripts first, then:

```
python3 -I ../../tools/uproot-map/uproot_map.py <refdata> <out> <bwg.jar> <corgilib.jar>
```

## Counts

| Status | BWG | CorgiLib |
|---|---|---|
| OK | 2424 (84%) | 562 (96%) |
| DESC_CHANGED | 45 | 6 |
| MEMBER_MISSING | 116 | 13 |
| CLASS_MOVED | 68 | 0 |
| CLASS_MISSING | 189 | 4 |
| Points at CorgiLib or WTHIT (no 26.3 build) | 46 | – |

**Mixins:**
- BWG has 10. 8 resolve cleanly, and the 2 that don't are both surface generation.
- CorgiLib has 7. 6 resolve cleanly, and the odd one out is a mouse hook on `Minecraft.getOverlay()`.

## The dependency picture (corrected)

- **Oh The Trees You'll Grow** has a 26.3 build (12.0.3), but its API changed. `TreeFromStructureNBTConfig$Builder` and the other `levelgen/feature/configurations` classes are gone (15 refs). uproot has to shim BWG's calls into the **new** Oh The Trees You'll Grow. It does not have to bridge the old one.
- **TerraBlender** and **GeckoLib** have 26.3 builds.
  - GeckoLib moved packages, from `software/bernie/geckolib` to `com/geckolib` (68 refs). That's a pure package rename.
  - TerraBlender replaced `SurfaceRuleManager` with `MaterialRuleManager`.
- **CorgiLib** is the only dependency to bridge. It's 96% clean already, with 23 non-OK refs.
- **WTHIT** is an optional plugin (`wthit_plugins.json`). Leave it inert until WTHIT ships 26.3.
- terraform-wood-api is **not** a BWG dependency. Only Bountiful Fish uses it.

## Problem areas, in order of cost

1. **Surface rules → material rules (the one real redesign).** 26.3 removed `SurfaceRules`, `SurfaceSystem` and `SurfaceRuleData`, and dropped `ChunkGenerator.buildSurface`. Surface rules are now a data-driven registry, `worldgen/material_rule`, in `net.minecraft.world.level.levelgen.material`. In that registry, `MaterialRule.compile(MaterialRuleContext) → RuleEvaluator` replaces `RuleSource.apply(Context) → SurfaceRule`.
   - The shape is close, so the shim plan is:
     - An adapter makes BWG's custom rule sources (`BandsRuleSource`, `WeightedRuleSource`, `BetweenRepeatingNoiseRange`) implement `MaterialRule`, with codecs registered in the new rule and condition registries.
     - `SurfaceRules.*` builder calls map onto `MaterialRules.*`, which has the same vocabulary: `ifTrue`, `sequence`, `state`, `stoneDepthCheck`, `noiseCondition`, `isBiome`, and so on.
     - Static conditions like `ON_FLOOR` and `UNDER_FLOOR` map to the `stoneDepthCheck(...)` equivalents.
     - `TerraBlenderRegister`'s `SurfaceRuleManager.addSurfaceRules` call goes to `MaterialRuleManager`.
   - The two broken mixins, `SurfaceSystemMixin` and `ChunkStatusTasksMixin`, hook the old surface pass. Their behaviour needs re-expressing against the new material-rule evaluation. That is the one place real per-mod code is needed.
   - `KeyDispatchDataCodec` is gone (3 refs). Plain `MapCodec` replaces it.
2. **Feature API (26.3 `configured_feature` → `feature`).** `ConfiguredFeature`, `FeaturePlaceContext`, `NoneFeatureConfiguration`, `RandomPatchConfiguration` and `FallenTreeConfiguration$Builder` are missing. 11 `Feature.*` statics and 6 `VegetationFeatures` members are missing too. This matches the agent finding that 26.3 merged configured features into `worldgen/feature`. BWG's ~200 feature JSON files in `data/` need the same path and format conversion.
3. **Mechanical renames** (rename table, low risk):
   - `EntityType.X → EntityTypes.X` (45)
   - GeckoLib package move (68)
   - `FarmBlock → FarmlandBlock`, `DirtPathBlock → PathBlock`
   - `SnowyDirtBlock` and `SpreadingSnowyDirtBlock` (to be identified)
   - `HoeItem` removed (rewrite its `instanceof` checks)
   - `Bucketable` moved package
   - `ImprovedNoise` is missing (find its replacement)
   - `PathType.DAMAGE_FIRE`: needs a decision; candidates are `DAMAGING` and `DAMAGE_CAUTIOUS`
4. **Signature changes (45):** `BonemealableBlock.performBonemeal` gained a `BonemealSource` parameter. `BiomeColors` and `BlockPredicate` changed too. Each needs a small bridge method that fills in the new arguments.
5. **Fabric API removals:**
   - Client: `ParticleFactoryRegistry`, `ColorProviderRegistry`, `BlockRenderLayerMap` and `EntityModelLayerRegistry`.
   - Content: `FabricItemGroup` and `TradeOfferHelper`.
   - Each one needs a shim onto its 26.3 replacement. The Fabric 26.1 rename table covers most of them.

## What this means for mms-uproot

- **Most of it is mechanical.** About 85% of references are already fine. Of the rest, a large share are renames or package moves, which a table handles.
- **Two subsystems need real shim code:** surface rules → material rules, and the feature-API merge. Both are worldgen, so this is BWG-specific work. It isn't a general render layer, and it confirms the build order: no rendering work is needed to get BWG running.
- **The tool's limits:**
  - It doesn't detect mod methods that **override** vanilla methods whose signature changed. Add that check next.
  - Candidate suggestions are heuristic.
  - Guava, fastutil, JOML and Mixin are not indexed.
