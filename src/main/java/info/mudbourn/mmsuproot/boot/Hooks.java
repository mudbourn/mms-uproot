package info.mudbourn.mmsuproot.boot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import info.mudbourn.mmsuproot.disable.Disabler;
import info.mudbourn.mmsuproot.remap.MixinConfigs;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts Uproot between Knot and the mods' bytes, with no agent and no written files.
 *
 * <p>Class bytes: Knot gets them from the game provider's entrypoint transformer first, for both
 * normal class loading and for Mixin reading a mixin class, so one subclass there serves both.
 *
 * <p>Resources: Mixin reads its config through {@code FabricLauncherBase.getLauncher()}, so a
 * proxy around the launcher serves the softened config and the rewritten refmap. The proxy is not
 * Knot, so the two places in Loader that test {@code instanceof Knot} see something else while it is
 * installed: {@code MixinServiceKnot.getName} (a label) and {@code FabricLoaderImpl.prepareModInit}
 * (a class loader sanity check that is skipped). To keep that window short {@link #release()} puts the
 * original launcher back once Mixin has read every config and prepared its mixins.
 */
final class Hooks {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");

	private final List<ModSource> sources;
	private final Map<String, ModSource> configs = new ConcurrentHashMap<>();
	private final Set<String> refmaps = ConcurrentHashMap.newKeySet();
	private final Map<String, ModSource> refmapOwners = new ConcurrentHashMap<>();
	private final AtomicBoolean errorHandlerRegistered = new AtomicBoolean();
	private final AtomicInteger rawReads = new AtomicInteger();
	private final Report report;
	private FabricLauncher knot;

	Hooks(List<ModSource> sources, net.fabricmc.api.EnvType side, Report report) {
		this.sources = sources;
		this.report = report;
		for (ModSource s : sources) {
			for (String cfg : s.mixinConfigs(side)) configs.put(cfg, s);
		}
	}

	void install(GameProvider provider) throws ReflectiveOperationException {
		Field tf = findField(provider.getClass(), GameTransformer.class);
		tf.setAccessible(true);
		GameTransformer original = (GameTransformer) tf.get(provider);
		tf.set(provider, new Transformer(original));

		FabricLauncher launcher = FabricLauncherBase.getLauncher();
		knot = launcher;
		noteEarlyClasses(launcher);
		FabricLauncher proxy = (FabricLauncher) Proxy.newProxyInstance(FabricLauncher.class.getClassLoader(), new Class<?>[] {FabricLauncher.class},
				(p, method, args) -> {
					try {
						if (method.getName().equals("getResourceAsStream") && args != null && args.length == 1) {
							InputStream replaced = resource((String) args[0], launcher);
							if (replaced != null) return replaced;
						} else if (method.getName().equals("getClassByteArray") && args != null && args.length == 2 && !(Boolean) args[1]) {
							byte[] bytes = remapped((String) args[0]);
							if (bytes != null) {
								rawReads.incrementAndGet();
								return bytes;
							}
						}
						return method.invoke(launcher, args);
					} catch (InvocationTargetException e) {
						throw e.getCause();
					}
				});
		setLauncher(proxy);
	}

	private static void setLauncher(FabricLauncher launcher) throws ReflectiveOperationException {
		Field lf = FabricLauncherBase.class.getDeclaredField("launcher");
		lf.setAccessible(true);
		lf.set(null, launcher);
	}

	/**
	 * Puts Knot back. Mixin has by now read every config and refmap (at init) and prepared every mixin
	 * (on the first class it transformed), and class reads with transformers go through Knot's own
	 * pipeline, which already holds Uproot's transformer. A mixin config another mod adds after this
	 * point would be read unsoftened and unmapped; the report says so.
	 */
	void release() {
		FabricLauncher restore;
		synchronized (this) {
			restore = knot;
			knot = null;
		}
		if (restore == null) return;
		try {
			setLauncher(restore);
			report.note("Launcher proxy removed after the mods were initialised; Mixin read " + rawReads.get()
					+ " mod classes through it, remapped. A mixin config added after that point is not softened or remapped.");
			LOGGER.info("Uproot removed its launcher proxy; Mixin read {} mod classes through it", rawReads.get());
		} catch (ReflectiveOperationException e) {
			LOGGER.warn("Could not restore the original launcher", e);
		}
	}

	/** Classes of a target mod that were loaded before this hook could rename them stay in their old names. */
	private void noteEarlyClasses(FabricLauncher launcher) {
		for (ModSource s : sources) {
			int early = 0;
			try {
				for (String cls : s.classes.keySet()) if (launcher.isClassLoaded(cls.replace('/', '.'))) early++;
			} catch (RuntimeException e) {
				LOGGER.debug("Could not check for early loaded classes of {}", s.id, e);
				continue;
			}
			if (early > 0) s.report.notes.add(early + " classes were loaded before Uproot's hook and bypass the transformer");
		}
	}

	private static Field findField(Class<?> cls, Class<?> type) throws NoSuchFieldException {
		for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) if (f.getType() == type) return f;
		}
		throw new NoSuchFieldException(type.getName() + " in " + cls.getName());
	}

	private byte[] remapped(String name) {
		String internal = name.replace('.', '/');
		for (ModSource s : sources) {
			if (!s.classes.containsKey(internal)) continue;
			try {
				return s.remapped(internal, false);
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Could not remap {} for Mixin", name, e);
			}
		}
		return null;
	}

	private InputStream resource(String name, FabricLauncher launcher) throws IOException {
		ModSource owner = configs.get(name);
		boolean refmap = !refmaps.isEmpty() && refmaps.contains(name);
		if (owner == null && !refmap) return null;

		byte[] raw;
		try (InputStream in = launcher.getResourceAsStream(name)) {
			if (in == null) return null;
			raw = in.readAllBytes();
		}
		String text = new String(raw, StandardCharsets.UTF_8);

		if (owner != null) {
			registerErrorHandler();
			String[] refmapName = new String[1];
			String soft = MixinConfigs.soften(text, refmapName, Disabler::dropsMixin, owner.id.equals(System.getProperty("uproot.strictMixins")));
			if (soft == null) {
				owner.report.mixinIssues.add(name + ": config could not be parsed, left as is");
				return null;
			}
			if (refmapName[0] != null) {
				refmaps.add(refmapName[0]);
				refmapOwners.put(refmapName[0], owner);
			}
			owner.report.mixinConfigs.incrementAndGet();
			return new ByteArrayInputStream(soft.getBytes(StandardCharsets.UTF_8));
		}

		ModSource refOwner = refmapOwners.get(name);
		return new ByteArrayInputStream(refOwner.namer.rewriteRefmap(text).getBytes(StandardCharsets.UTF_8));
	}

	/** Lets the report learn about mixins Mixin itself fails to prepare or apply. */
	private void registerErrorHandler() {
		if (!errorHandlerRegistered.compareAndSet(false, true)) return;
		try {
			org.spongepowered.asm.mixin.Mixins.registerErrorHandlerClass(MixinErrors.class.getName());
		} catch (Throwable t) {
			LOGGER.warn("Could not register Uproot's mixin error handler", t);
		}
	}

	/** Serves remapped bytes for classes of target mods, and everything else as before. */
	private final class Transformer extends GameTransformer {
		private final GameTransformer original;

		Transformer(GameTransformer original) {
			this.original = original;
		}

		@Override
		public void locateEntrypoints(FabricLauncher launcher, List<Path> gameJars) {
			original.locateEntrypoints(launcher, gameJars);
		}

		@Override
		public byte[] transform(String className) {
			byte[] patched = original.transform(className);
			if (patched != null) return patched;

			String internal = className.replace('.', '/');
			for (ModSource s : sources) {
				if (!s.classes.containsKey(internal)) continue;
				try {
					return s.remapped(internal, true);
				} catch (IOException | RuntimeException e) {
					LOGGER.warn("Could not remap {}; loading it as is", className, e);
					return null;
				}
			}
			return null;
		}
	}
}
