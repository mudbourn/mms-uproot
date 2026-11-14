package info.mudbourn.mmsuproot.boot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import info.mudbourn.mmsuproot.data.DataShim;
import info.mudbourn.mmsuproot.disable.Disabler;
import info.mudbourn.mmsuproot.map.GameClasses;
import info.mudbourn.mmsuproot.map.MappingFetcher;
import info.mudbourn.mmsuproot.map.NameTable;
import info.mudbourn.mmsuproot.map.Renames;
import info.mudbourn.mmsuproot.remap.AccessWideners;
import info.mudbourn.mmsuproot.remap.ModClasses;
import info.mudbourn.mmsuproot.remap.Namer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.lib.classtweaker.api.ClassTweakerReader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uproot's earliest hook. Loader builds language adapters after it resolves mods but before it
 * reads access wideners and starts Mixin, so the constructor runs in that gap without any JVM
 * arguments. It finds the mods that still use intermediary names, moves their access wideners to
 * the runtime names and installs the hooks that rename their classes as they load.
 */
public final class UprootBoot implements LanguageAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	private static final Map<String, Report.Entry> MIXIN_CONFIG_OWNERS = new ConcurrentHashMap<>();
	private static volatile Report report;
	private static volatile Hooks hooks;

	public UprootBoot() {
		FabricLauncher launcher = FabricLauncherBase.getLauncher();
		String runtimeNs = launcher.getMappingConfiguration().getRuntimeNamespace();
		EnvType side = launcher.getEnvironmentType();
		Path dir = FabricLoader.getInstance().getGameDir().resolve("mms-uproot");
		report = new Report(dir.resolve("report.txt"));
		Disabler.init(report);
		LOGGER.info("Uproot boot hook running, runtime namespace {}", runtimeNs);

		List<ModContainerImpl> targets = new ArrayList<>();
		List<ModContainerImpl> modern = new ArrayList<>(); // 26.1 and 26.2 builds: Mojang names already, 26.3 API changes only
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			ModContainerImpl impl = (ModContainerImpl) mod;
			if (ModSource.skip(impl)) continue;
			if (ModSource.refersToIntermediary(impl, report)) targets.add(impl);
			else if (ModSource.builtForOlder26(impl, report)) modern.add(impl);
		}

		if (targets.isEmpty() && modern.isEmpty()) {
			LOGGER.info("No mod refers to intermediary names or targets an older 26.x; Uproot has nothing to remap");
			report.note("No target mods found.");
			report.write();
			return;
		}
		LOGGER.info("Uproot target mods: {}, 26.x builds: {}", targets.stream().map(m -> m.getMetadata().getId()).toList(),
				modern.stream().map(m -> m.getMetadata().getId()).toList());

		// Only intermediary names need the name table; a 26.x build is already in Mojang names.
		NameTable table = targets.isEmpty() ? new NameTable() : MappingFetcher.obtain(dir.resolve("cache"));
		if (table == null) {
			report.note("The 1.21.11 name table could not be built, so no 1.21.11 mod was remapped. Targets: "
					+ targets.stream().map(m -> m.getMetadata().getId()).toList());
			for (ModContainerImpl mod : targets) detachAccessWidener(mod.getInfo());
			targets.clear();
			table = new NameTable();
			if (modern.isEmpty()) {
				report.write();
				return;
			}
		}

		GameClasses game = new GameClasses(launcher.getTargetClassLoader(), launcher.getClassPath());
		Renames renames = targets.isEmpty() ? null : Renames.load();
		Renames renames26 = modern.isEmpty() ? null : Renames.load("/uproot/renames-26x.txt");
		report.note("Minecraft classes indexed: " + game.size());

		// Index every target before any name is mapped, so a class of one target is never renamed for another.
		ModClasses modClasses = new ModClasses();
		List<ModSource> sources = new ArrayList<>();
		for (ModContainerImpl mod : targets) prepare(mod, new Namer(table, renames, game, modClasses, report.mod(mod.getMetadata().getId())), true, sources);
		for (ModContainerImpl mod : modern) {
			String id = mod.getMetadata().getId();
			Report.Entry entry = report.mod(id);
			entry.notes.add("built for an older 26.x: Mojang names kept, 26.3 rules from renames-26x.txt");
			prepare(mod, new Namer(table, renames26, game, modClasses, entry), false, sources);
		}
		for (ModSource src : new ArrayList<>(sources)) {
			try {
				handleAccessWidener(src, runtimeNs);
				for (String cfg : src.mixinConfigs(side)) MIXIN_CONFIG_OWNERS.put(cfg, src.report);
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Uproot could not prepare {}", src.id, e);
				src.report.notes.add("could not prepare: " + e);
				sources.remove(src);
			}
		}

		for (ModSource src : sources) MixinAudit.run(src, side);

		try {
			hooks = new Hooks(sources, side, report);
			hooks.install(FabricLoaderImpl.INSTANCE.getGameProvider());
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Uproot could not install its class hooks", e);
		}

		for (ModSource src : sources) {
			LOGGER.info("{}: {} classes indexed, {} mixin issues found before launch", src.id, src.classes.size(), src.report.mixinIssues.size());
		}
		if (Boolean.getBoolean("uproot.auditAll")) {
			Thread audit = new Thread(() -> {
				for (ModSource src : sources) src.auditAll();
				LOGGER.info("Uproot audited every class of {} mods", sources.size());
			}, "mms-uproot-audit");
			audit.setDaemon(true);
			audit.start();
		}
		report.write();
	}

	/** Indexes one mod. Only a 1.21.11 mod has data in the old formats, so only it gets the data shim. */
	private static void prepare(ModContainerImpl mod, Namer namer, boolean legacyData, List<ModSource> sources) {
		String id = mod.getMetadata().getId();
		Report.Entry entry = report.mod(id);
		ModSource src = new ModSource(mod, namer, entry);
		if (legacyData) DataShim.target(id, entry);
		try {
			src.index();
			sources.add(src);
			entry.notes.add("indexed " + src.classes.size() + " classes");
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Uproot could not prepare {}", id, e);
			entry.notes.add("could not prepare: " + e);
		}
	}

	/** Called once the game and mods are initialising; see {@link Hooks#release()}. */
	static void release() {
		Hooks h = hooks;
		if (h != null) h.release();
	}

	/** The id of the mod Uproot rewrites that the mixin config belongs to, or null. */
	static String ownerOf(String config) {
		Report.Entry entry = MIXIN_CONFIG_OWNERS.get(config);
		return entry == null ? null : entry.id;
	}

	static void recordMixinFailure(String config, String mixin, String what) {
		Report.Entry entry = MIXIN_CONFIG_OWNERS.get(config);
		if (entry != null) entry.mixinIssues.add(mixin + ": " + what);
		LOGGER.warn("Mixin {} ({}) {}", mixin, config, what);
	}

	/** Loader refuses an access widener from another namespace, so hand it a remapped copy instead. */
	private static void handleAccessWidener(ModSource src, String runtimeNs) throws IOException {
		LoaderModMetadata meta = src.mod.getInfo();
		String location = meta.getClassTweaker();
		if (location == null) return;

		Path path = src.mod.findPath(location).orElse(null);
		if (path == null) return;

		String text = Files.readString(path);
		String ns = AccessWideners.namespace(text);
		if (ns == null || ns.equals(runtimeNs)) return;

		String remapped = AccessWideners.remap(text, runtimeNs, src.namer, src.report, location);
		ClassTweakerReader.create(FabricLoaderImpl.INSTANCE.getClassTweaker()).read(remapped.getBytes(StandardCharsets.UTF_8), runtimeNs);
		detachAccessWidener(meta);
	}

	private static void detachAccessWidener(LoaderModMetadata meta) {
		try {
			Field field = meta.getClass().getDeclaredField("classTweaker");
			field.setAccessible(true);
			field.set(meta, null);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Uproot could not detach the access widener of " + meta.getId(), e);
		}
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) {
		throw new UnsupportedOperationException("Uproot's language adapter is a boot hook, not an entrypoint adapter");
	}
}
