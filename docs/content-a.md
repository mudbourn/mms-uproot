# content-a findings (MC 1.21.11 -> 26.3)

Method: unzipped each jar, read fabric.mod.json, mixin configs, refmaps, AW, constant pools of mixin classes; Modrinth API for newest builds and licences; downloaded (inspect only) the newer 26.x builds and diffed their mixin sets; minecraft.wiki Java_Edition_26.3 for data changes.

## Table

| Slug | Licence / source | Mixins (@Redirect/@Overwrite) | Content registered | Data side | Newest build | Verdict |
|---|---|---|---|---|---|---|
| absolut-revive | GPL-3.0 (jar+Modrinth); GitHub Goetic-AlgoFlow/AbsolutRevive | 12 (6 client). 0 / 0 | none (damage model, GUI, recipe) | 1 recipe, pack_format 34 | v1.2.2 for 26.1-26.1.2 | use-newer-build |
| basicweapons | GPL-3.0; GitHub Khazoda/basic-weapons | 10 (4 client). 0 / 0 | many items, enchantments, tags, advancements | 192 data json, 189 asset json, custom pack loader | 2.4.2+26.1 | use-newer-build |
| bettercoppergolem | GPL-3.0; GitHub GhostSHAURMA/BetterCopperGolem | 4. 1 Redirect / 0 | none (AI tweak, config) | none | 1.1.8 for 26.1.x (Java 25) | use-newer-build |
| bubblellaneous | CC-BY-SA-4.0; GitHub bbfh-dev/bubblellaneous-pack | 0 (no classes) | resource pack + datapack only (994 data json, 4815 mcfunction, 3596 asset json) | pack_format 94.1, max [107,1] | 3.0.8 datapack/RP lists 26.3 (max_format [121,1]) | use-newer-build (trivial) |
| chefs-delight | MIT; no source link | 1 (empty ExampleMixin on MinecraftServer). 0 / 0 | villager profession, trades, 10 structure NBT, loot, tags | structure NBT, villager_trade | 1.0.6-26.2 | use-newer-build |
| computerpc | CC0 (1.21.11 jar); 26.1.1 jar says LGPL-2.1+; GitHub yukocha0/Computerpc-MinecraftBrowserMod | 0 mixins | blocks, block entity, items, custom payloads, BER | 3 json | 1.1.0-26.1.1 (jij MCEF 2.2.0) | use-newer-build (hard to bridge) |
| cosmetic-armor-updated | MIT; GitHub Mhaks02/cosmetic-armor | 2 client. 2 Redirect / 0 | trinket slots | 7 json (trinkets tags) | 1.8.0 for 26.1/26.1.1 | use-newer-build |
| craftable-saddles-backport | ARR (Unknown); datapack | 0 | one recipe | pack_format 48; old `{"item":..}` ingredient syntax | same jar tagged to 26.1.1 | easy (rewrite 1 recipe) |
| crawl-on-demand | ARR; source not public | 2 (1 common, 1 fabric). 0 / 0 | none | pack ok (min_format [101,1]) | 1.3.1 for 26.2 (needs `minecraft <26.3`) | use-newer-build |
| enchanted-utilities | ARR; datapack, no source | 0 | datapack: enchantment overrides, recipes, loot, tick function | min_format [94,0], max_format [101,1] | 1.2.0 (same jar covers 1.21.11-26.1.2) | easy (pack metadata plus data check) |
| expanded-weaponry | jar 0.8 says ARR, Modrinth/26.2 build says MIT; GitHub Kielson-Official/_Kielson-s-Enhanced-Combat | 1 (ArrowEntityMixin). 0 / 0 | many weapons, advancements, weapon_attributes | 147 data json, 137 assets | 1.0.3-26.2 (4 mixins, own jij kielsons-api) | use-newer-build |
| guard-villagers-(fabricquilt) | MIT; no source link on Modrinth | 3. 0 / 0 | guard entity, goals, loot | 7 json | 2.1.3-26.2 (jij midnightlib 1.9.3) | use-newer-build |

## Cross-mod patterns

- The 1.21.11 jars hold intermediary names (class_/method_/field_ in mixin strings, AWs and bytecode). Some jars (absolut-revive, bettercoppergolem, chefs-delight, guardvillagers, expanded-weaponry, computerpc) have no refmap, so the mixin strings are already intermediary and the remapper must rewrite annotation strings itself.
- Every mod that has a 26.x build there is a mojmap-named build of the same mixin set. Diffing new vs old is the best source for the retarget table (see mappings below).
- Package/class moves visible in the 26.x builds that uproot must map generically: `ResourceLocation` -> `net.minecraft.resources.Identifier`; `GuiGraphics` -> `GuiGraphicsExtractor`; entity subpackages (`world.entity.animal.golem.IronGolem`/`CopperGolem`, `monster.zombie.Zombie`, `monster.piglin.Piglin`, `monster.spider.Spider`, `monster.illager.AbstractIllager`, `projectile.arrow.AbstractArrow`/`Arrow`); `ItemStack`/`ItemInHandRenderer` unchanged name. 26.1+ requires Java 25 (every 26.x build lists `java >=25`).
- Fabric mod.json version gates (`minecraft: ~26.2`, `>=26.2 <26.3`, `>=26.1 <=26.1.2`) block loading of 26.1/26.2 builds on 26.3. Uproot needs a generic dependency-range rewrite for `minecraft` (and `java`).
- Datapack/resource-only mods (bubblellaneous, enchanted-utilities, craftable-saddles) have no classes at all; they need only pack.mcmeta `min_format`/`max_format` patching, not bytecode work. Bubblellaneous already ships a 26.3 pack.
- 26.3 data changes (minecraft.wiki 26.3): root advancements must declare `background` and non-root must not; block state providers can be standalone and several types are renamed; new data components (`attack_animation`, `block_transformer`). None of my 12 jars has a root advancement without background (checked basicweapons, expanded-weaponry, chefs-delight, guardvillagers), and none uses worldgen block state providers.
- Mods that override vanilla JSON (enchanted-utilities replaces minecraft:protection, thorns, unbreaking, shears, shield, flint_and_steel, wolf_armor recipes and several vanilla chest loot tables) will silently freeze stale vanilla data on 26.3. Check them against 26.3 vanilla.

## Notes per mod

### absolut-revive (GPL-3.0, open) - use-newer-build
- Mixins (intermediary strings, no refmap, 12): ItemEntity (method_5694 playerTouch), LivingEntity (method_6095 isInvulnerableTo, uses DamageTypeTags), Mob (method_5980 setTarget), LivingEntity dimensions (method_18377 getDimensions with Pose), Player (method_5749 readAdditionalSaveData / method_5652 addAdditionalSaveData using ValueInput/ValueOutput), ServerGamePacketListenerImpl (method_12076 handleContainerClick), client: Minecraft.setScreen (method_1507, InventoryScreen), MouseHandler (method_1606 turnPlayer), AvatarRenderer + LivingEntityRenderer + LivingEntityRenderState (Fabric `RenderStateDataKey`, method_62355 extractRenderState; PoseStack rotations), PlayerModel/HumanoidModel (method_62110 setupAnim, many ModelPart fields). 11 @Inject, 1 @ModifyVariable. AW targets: class_1740 (method_66345), class_1665 (AbstractArrow method_65059/field_7576).
- Client render-state mixins are the exposure (render state API is the area most likely to move again between 26.1 and 26.3). v1.2.2 (26.1-26.1.2, depends `minecraft >=26.1 <=26.1.2`) keeps the identical 12-class set in mojmap names and the same mixin shapes (setupAnim, AvatarRenderer, PoseStack/Axis). So the 26.1 -> 26.3 gap is small; bridging from 1.21.11 would repeat the 1.21.11 -> 26.1 work for nothing.
- Uproot needs: version-range rewrite plus verify the 4 render mixins and the `RenderStateDataKey` use. Source is GPL on GitHub, so porting yourself is realistic.

### basicweapons (GPL-3.0, open) - use-newer-build
- 1.21.11 jar: 10 mixins (7 @Inject, 3 @ModifyArg, 1 Accessor). Server/client pack-source mixins (ServerPacksSource method_45286, CreateWorldScreen method_64244, Minecraft ClientPackFinder) inject a custom `ResourceAndDatapackCustomLoader` into the `PackRepository` constructor via @ModifyArg. Enchantment (canEnchant/isPrimaryItem), ItemStack tooltip (method_7950), Monster/Mob spawn gear (method_5964 populate equipment; Piglin/Zombie), Player (attack cooldown method_7261, method_7324), HumanoidModel and ItemInHandRenderer (method_3233 renderItem, method_3228).
- 2.4.2+26.1 jar (335 KB, multiloader, includes neoforge toml) keeps the same 10-class mixin list with mojmap names; moves are `Identifier`, `world.entity.monster.zombie.Zombie`, `monster.piglin.Piglin`, `HumanoidRenderState`. It targets 26.1.x; remaining gap to 26.3 is small.
- Data: 192 data json (weapon_attributes, enchantment, recipe, tags; includes `farmersdelight` tag integrations), pack.mcmeta `pack_format 8` (loaded by its own loader so unchanged). Enchantments and recipes may need a format check against 26.3 vanilla; this is the largest data surface in my group.
- Uproot needs: use 26.1 jar as base; only retarget pack-repository @ModifyArg if PackRepository ctor changes.

### bettercoppergolem (GPL-3.0, open) - use-newer-build
- 1.21.11 jar: 4 mixins on CopperGolem (class_11573), CopperGolemAi (class_11574, @ModifyConstant on method_72491), TransportItemsBetweenContainers (class_11568; @Redirect, @ModifyArg/Variable/Constant on the private methods method_72448/72405/72402/72428/72403/72434/72408) and an accessor on `ItemContainerContents.items` (field_49338). It reaches into private lambda-like methods of a brand-new vanilla behaviour, so it is the most fragile mixin set in the group.
- 1.1.8 build for 26.1.x (934 KB, same version string, Java 25) uses the same four classes (world.entity.animal.golem.CopperGolem/CopperGolemAi, world.entity.ai.behavior.TransportItemsBetweenContainers). Copper golem AI may have been tuned again by 26.2/26.3; unverified.
- Uproot needs: the 26.1 jar plus a check of the private method signatures in TransportItemsBetweenContainers against 26.3. If they shifted, patch or disable per mod (config-only mod, safe to drop).

### bubblellaneous (CC-BY-SA-4.0, open) - use-newer-build
- The Fabric jar is a wrapper: fabric.mod.json declares no entrypoints, zero classes, just resource/data packs (994 data json, 4815 .mcfunction files, 3596 asset json) with pack_format 94.1 and overlay entries.
- Modrinth lists 3.0.8 (datapack, resource pack, papermc variants) with `pack.mcmeta` max_format [121,1] and a `minecraft max 26.3` meta and overlay dirs (`1.21.11__26.1.2`, `1.21.11__26.2`, `26.2-s...`). No `+mod` 3.0.8 jar exists, so either use the 3.0.8 datapack+RP separately or have uproot just raise `max_format` on the 3.0.6 jar. Licence is share-alike, so redistribution is allowed with attribution.
- Uproot needs: nothing for bytecode. Prefer the 3.0.8 pack.

### chefs-delight (MIT, no source link) - use-newer-build
- 1.21.11 jar: single `ExampleMixin` (template leftover on MinecraftServer, method_3735) and a jij `structurized_reborn-1.21.5-01.jar`. Registers a villager profession (Chef/Cook), 9 NBT houses (plains/taiga/snowy/desert/savanna x chef/cook), loot tables, `acquirable_job_site` POI tag, `villager_trade`/`trade_set` data, many optional-mod tags (create, tconstruct, sereneseasons, sable, origins, farmersdelight).
- 1.0.6-26.2 jar (153 KB, `minecraft ~26.2`, `java >=25`, no jij) is a straight port. Structure NBT upgrades by vanilla DataFixer on load. A mixin-free data port.
- Uproot needs: only a range rewrite on the 26.2 jar; or drop the dead ExampleMixin.

### computerpc (CC0 per 1.21.11 jar; 26.1.1 jar claims LGPL-2.1-or-later) - use-newer-build (bridging from 1.21.11 = hard)
- No mixins but heavy API use: custom blocks and a BlockEntity with BlockEntityRenderer (class_827/class_5614), custom payloads (class_8710), Screen/widgets (GuiGraphics class_332, EditBox class_342, Button class_4185, AbstractSliderButton class_357), `RenderType`/`RenderPipelines`, `class_11659` (OrderedRenderCommandQueue / SubmitNodeCollector) and ValueInput/Output. Embeds MCEF 2.2.0 (Chromium; downloads native binaries from Keksuccino mirror).
- 26.1.1 build (1.1.0-26.1.1) uses `GuiGraphicsExtractor`, `SubmitNodeCollector$CustomGeometryRenderer`, `ModelFeatureRenderer$CrumblingOverlay`, `RenderTypes`, `CameraRenderState`, `KeyEvent`/`MouseButtonEvent`/`CharacterEvent` input events, and bundles a different MCEF jar (2.2.0-26.1.1). Uproot would need shims for the GUI draw method renames and BER submit API, plus an MCEF 26.x-compatible jar (MCEF latest on Modrinth listing was 2.1.6-1.21.4; the 26.1.1 jar here is a jij build). 
- Uproot needs: use the 26.1.1 jar, then bridge 26.1 -> 26.3 on GUI and BER; MCEF's native layer (JCEF/GL) is the real risk. Unverified whether MCEF runs on 26.3.

### cosmetic-armor-updated (MIT, open) - use-newer-build
- 2 client mixins, both @Redirect on `ItemStack.isEmpty`/`LivingEntity.getItemBySlot` inside the armor layer (HumanoidArmorLayer, class_909 method_65568 / LivingEntityRenderer class_922 method_62355 extractRenderState). Hard dependency on trinkets: the pack uses "Trinkets Updated" (3.11.0-beta.2+1.21.11); that project now lists 26.2 and 26.3, so the dependency exists.
- 1.8.0 (26.1/26.1.1, 15.7 KB) retargets to `HumanoidMobRenderer.getEquipmentIfRenderable` and `LivingEntity.getItemBySlot`. Tiny. 
- Uproot needs: use 1.8.0 plus a redirect retarget to the humanoid armor-render hook if it moved again.

### craftable-saddles-backport (ARR datapack) - easy (rewrite)
- One shaped recipe `crafting:...` (leather/iron_ingot). It uses the pre-1.21.2 ingredient object `{"item": "minecraft:leather"}`, which is not valid in 1.21.2+ (string ids) so it is probably already broken on 1.21.11 (unverified). Pack_format 48.
- Better to replace with one 12-line recipe datapack of our own. Check first whether 26.x vanilla already has a saddle recipe; unverified.

### crawl-on-demand (ARR, no public source) - use-newer-build
- 1.21.11: PlayerPoseMixin (Player.updatePlayerPose / method_7318, Pose class_4050) and PlayerCrawlMixin (@Unique field), 1 @Inject. Client entry point.
- 1.3.1-26.2 build: PlayerPose, PlayerCrawl plus new DiscreteWhenCrawlingMixin (Player; Entity discrete-ness) and `GuiScreenAccessor` for `Gui`; depends on `fabric-api >=0.149.2+26.2` and `minecraft >=26.2 <26.3`. pack.mcmeta min_format [101,1].
- Uproot needs: version-range override (<26.3), then confirm that Player pose logic and Gui field in the accessor still exist on 26.3.

### enchanted-utilities (ARR datapack) - easy
- Content: enchantment overrides (protection, thorns, unbreaking), recipes (shears, shield, flint_and_steel, wolf_armor, netherite nautilus armor), vanilla chest loot overrides, a tick/load function (shield thorns with `execute on attacker ... damage @s ... thorns`). min_format [94,0]/max_format [101,1] (1.21.11 to 26.2).
- Uproot needs: raise `max_format` to cover 26.3 and diff each overridden vanilla JSON against 26.3 vanilla (stale overrides would hide upstream changes). The 26.3 `Enchantments` change (apply to items being damaged while protecting undead from the sun) shows enchantment effects are still moving.

### expanded-weaponry (jar says ARR; Modrinth/26.2 jar say MIT; open) - use-newer-build
- 1.21.11 jar is 0.8 (deps `minecraft >=1.21.6`): one mixin (ArrowEntityMixin, AbstractArrow.<init> with Arrow `class_1667` and `getOwner` method_24921), 1 @Inject, AW on class_1665 (method_65059, field_7576) and class_1740 (method_66345 Map creator); jij kielsons-api 0.8.1. Registers many weapons, advancements, weapon_attributes (147 data json).
- Latest is 1.0.3-26.2 (314 KB), 4 mixins: ArrowEntity (now `world.entity.projectile.arrow.AbstractArrow`/`Arrow`), CreativeModeTab (WrapOperation via MixinExtras on `Output.accept`), LivingEntity (ShieldItem/EquipmentSlot), ShieldDecorationRecipe (CustomRecipe, BannerPatternLayers, DataComponents). Large jump from 0.8 to 1.0.3 means game-play changes, not just a port. Deps on kielsons-api 1.3.1-26.2.
- Uproot needs: use the 26.2 jar and fix version ranges; confirm the ShieldDecoration/CraftingInput recipe API on 26.3.

### guard-villagers-(fabricquilt) (MIT) - use-newer-build
- 1.21.11: 3 mixins (Mob / MobEntity: Mob.refreshDimensions-style constructors via class_1308 method_5980 setTarget, ServerWorld: goal registration on class_1937 `addFreshEntity` method_8649 for Raider/Spider/Animal/AvoidEntityGoal, VillagerTaskListProvider class_4129 method_19023/19024 using GateBehavior); 3 @Inject. AW covers LivingEntity.method_6060, GoalSelector fields on Mob (class_1355 field_6185/6201), Villager field_18535, TargetGoal (class_1366 field_6509), Entity method_5653. Jij midnightlib 1.9.2.
- 2.1.3-26.2 (320 KB, `java >=25`, midnightlib 1.9.3+26.2-pre-3) has the same three mixins. It references `world.entity.monster.spider.Spider`, `monster.illager.AbstractIllager`, `world.entity.animal.golem.IronGolem`, `world.entity.Targeting`. The other (NeoForge) Guard Villagers is already on 26.3 (5.0.0), custom licence, so upstream design is stable and the Fabric port may follow.
- Uproot needs: the 26.2 jar, plus verification of GateBehavior order/running policy and the AW members. Note upstream (seymourimadeit) is not MIT; the Fabric fork is.

## Unverified
- 26.3 vanilla internals (render-state API, PackRepository ctor, TransportItemsBetweenContainers, GateBehavior, Player pose methods): no 26.3 deobfuscated source was read; judged from 26.1/26.2 builds and the 26.3 wiki changelog only.
- Whether MCEF 2.2.0 works on 26.3 or has a newer 26.2/26.3 build.
- Whether the craftable-saddles `{"item":..}` ingredient works at all on current versions.
- GitHub source contents and ports (not browsed; relied on Modrinth links and diffing the published 26.x jars). Source for crawl-on-demand, chefs-delight, guardvillagers (Fabric) and the datapack mods was not found.
- Exact pack_format ranges accepted by 26.3 (bubblellaneous 3.0.8 declares max [121,1]; cod 26.2 uses 101.1).
- The 26.1-based jars (absolut-revive 1.2.2, basicweapons 2.4.2, bettercoppergolem 1.1.8, cosmetic armor 1.8.0, computerpc 1.1.0) were not diffed against 26.2/26.3 vanilla at the member level.
