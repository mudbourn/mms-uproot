package info.mudbourn.mmsuproot.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import info.mudbourn.mmsuproot.boot.DeadHooks;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

/**
 * Mixin injectors of the mods Uproot rewrites that matched nothing (see {@link DeadHooks}). The classes the mixins target are loaded
 * here, without initialising them, so that every injector has been applied (and counted) by the time the check reads the list.
 * What is accepted on purpose is in {@code uproot/dead-hooks.txt}, each with a reason.
 */
final class DeadHookProbes {
	private DeadHookProbes() { }

	static List<Probe> all() {
		return List.of(Probe.once("mixins.no-dead-hooks", DeadHookProbes::check));
	}

	private static String check(Probe.Ctx c) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		int loaded = 0;
		List<String> unloadable = new ArrayList<>();
		for (Config config : Mixins.getConfigs()) {
			if (DeadHooks.ownerOf(config.getName()) == null) continue;
			for (String target : config.getConfig().getTargets()) {
				try {
					Class.forName(target.replace('/', '.'), false, loader);
					loaded++;
				} catch (ClassNotFoundException | LinkageError e) {
					unloadable.add(target + " (" + e.getClass().getSimpleName() + ")");
				}
			}
		}
		Map<String, List<String>> byMod = new TreeMap<>();
		int accepted = 0;
		for (DeadHooks.Dead dead : DeadHooks.dead()) {
			if (dead.owner().equals("?")) continue;
			if (DeadHooks.allowedReason(dead) != null) {
				accepted++;
				continue;
			}
			byMod.computeIfAbsent(dead.owner(), k -> new ArrayList<>()).add(dead.mixin() + "." + dead.handler() + " into " + dead.target() + " wanted " + dead.intended());
		}
		if (!byMod.isEmpty()) {
			// the whole list goes to a file next to the probe results; the result line carries the count and the first few
			StringBuilder all = new StringBuilder();
			byMod.forEach((mod, list) -> {
				all.append("== ").append(mod).append('\n');
				list.forEach(l -> all.append("  ").append(l).append('\n'));
			});
			try {
				java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mms-uproot").resolve("dead-hooks.txt");
				java.nio.file.Files.writeString(file, all.toString());
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
			int count = byMod.values().stream().mapToInt(List::size).sum();
			throw new Probe.Fail(count + " injector(s) matched nothing, listed in run/mms-uproot/dead-hooks.txt; per mod: "
				+ byMod.entrySet().stream().map(e -> e.getKey() + " " + e.getValue().size()).toList());
		}
		return "no injector of a rewritten mod is dead (" + loaded + " target classes loaded, " + accepted + " dead ones accepted in dead-hooks.txt, " + unloadable.size() + " targets that would not load: " + unloadable + ")";
	}
}
