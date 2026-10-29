# Nether Descent: 1.21.11 → 26.3 reference map

Nether Descent 1.0.11 is by Potion Studios, the same studio as Oh The Biomes We've Gone (BWG). I checked its 1.21.11 jar with the BWG mapping tool ([../../tools/uproot-map/](../../tools/uproot-map/)) against the same 26.3 jars. Generated 2026-10-29. Every reference is in [nd-1-21-11.csv](nd-1-21-11.csv), and every non-OK one is in [SUMMARY.md](SUMMARY.md).

- **Licence:** all rights reserved, so it can only be bridged.
- **Not in the pack yet.**
- **Builds:** the 1.21.11, 26.1.2 and 26.2 builds of 1.0.11 all came out between 2026-10-27 and 10-29.
- **Required dependencies:** Fabric API and Oh The Trees You'll Grow (OTTYG), which both have 26.3 builds.
- **Optional dependencies:** TerraBlender and Biolith have 26.3 builds. WTHIT doesn't, so leave its plugin inert.

## Counts (2007 references, 211 classes)

| OK | DESC_CHANGED | MEMBER_MISSING | CLASS_MOVED | CLASS_MISSING | NO_26_3_LIB (WTHIT) |
|---|---|---|---|---|---|
| 1718 (86%) | 26 | 51 | 35 | 150 | 27 |

**Mixins:** it has only 3.
- `NaturalSpawnerMixin` resolves cleanly.
- `WolfMixin` needs one descriptor change: `ItemStack.is(Item)`.
- `FlameFeatureRendererMixin` is a client render mixin. `AtlasManager.get` moved, and it needs a retarget.

## It shares BWG's shims

Almost all the non-OK references fall into the same groups as BWG:
- **Surface rules → material rules:** `SurfaceRules` and `KeyDispatchDataCodec`.
- **The OTTYG API change:** `TreeFromStructureNBTConfig$Builder`, 16 references.
- **The feature-API merge:** `Feature.*`, `ConfiguredFeature`, `FeaturePlaceContext`, `NoneFeatureConfiguration` and the `TreeConfiguration` builder.
- **The mechanical renames:** `EntityType` → `EntityTypes`, `HoeItem`, `ParticleFactoryRegistry`.

So it mostly comes free once BWG works.

Its own additional problems:
- **Nether vegetation:** `RootsBlock`, `FungusBlock`, `NetherForestVegetationConfig` and `WaterlilyBlock` are missing in 26.3.
- **Advancement predicates:** `EntityPredicate`, `LocationPredicate`, `BlockPredicate`, `MinMaxBounds` and `SimpleCriterionTrigger` moved out of `advancements.criterion`, and `ContextAwarePredicate` and `CriterionValidator` are gone. This matches the 26.2 predicate move to `advancements.predicates`.
- **Structure pieces:** `StructurePieceAccessor` is missing.
- **Block references:** 8 `Blocks.*` references changed descriptor.

## The 26.2 build (checked 2026-10-29)

The mapper now detects jars that already use Mojang names and checks them directly. I ran it on the 26.2 build of 1.0.11 ([nd-26-2.csv](nd-26-2.csv)):

| | OK | DESC_CHANGED | MEMBER_MISSING | CLASS_MOVED | CLASS_MISSING | WTHIT |
|---|---|---|---|---|---|---|
| 1.21.11 jar | 1718 | 26 | 51 | 35 | 150 | 27 |
| **26.2 jar** | **1803** | 16 | 22 | 0 | 100 | 27 |

- **All 3 mixins resolve on 26.3.**
- **Already fixed in the 26.2 build:** the OTTYG builder breaks, the advancement predicate move and the `EntityType` renames.
- **What's left is purely the 26.2 → 26.3 change set:**
  - **Material rules:** `SurfaceRules`, `KeyDispatchDataCodec`, TerraBlender `SurfaceRuleManager`. These are the same shims as BWG.
  - **Feature merge:** `Feature.*`, `ConfiguredFeature`, `FeaturePlaceContext`, `NoneFeatureConfiguration`, `NetherForestVegetationConfig`, the `TreeConfiguration` builder. Also the same as BWG.
  - **Fabric API registries removed in 26.3:**
    - `CompostableRegistry`, `StrippableBlockRegistry`, `TillableBlockRegistry`
    - `FuelValueEvents` and `FuelValues$Builder`
    - `FabricPotionBrewingBuilder` and `PotionBrewing$Builder`
    - These likely moved to data-driven registries or tags in 26.3. That's a new generic shim area, and the Frostiful, Scorchful and Mob AI Tweaks brewing need it too.
  - **Smaller misses:** `HoeItem`, `SignItem`, `StructurePieceAccessor`, `LootPoolSingletonContainer$Builder`, `ContextAwarePredicate`.

## Strategy

- **Bridge from its 26.2 build**, not the 1.21.11 one: 138 non-OK references instead of 262 (not counting WTHIT), and no mixin work.
- **Schedule it right after BWG.** It reuses the material-rule, feature and OTTYG shims.
- **The author may beat us to it.** They're releasing every few days, so a 26.3 build is likely before uproot is ready. Check Modrinth before investing in it.
