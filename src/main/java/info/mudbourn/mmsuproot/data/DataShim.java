package info.mudbourn.mmsuproot.data;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.util.Json;
import info.mudbourn.mmsuproot.util.PackFormat;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.minecraft.server.packs.PackResources;

/**
 * Serves the data and client assets of target mods in the shapes 26.3 reads, built in memory when the pack is first
 * asked for data and never written anywhere. The mod's own pack is wrapped where Fabric API hands it
 * to the pack repository, so the game's loaders need no change.
 */
public final class DataShim {
	private static final Map<String, Report.Entry> TARGETS = new ConcurrentHashMap<>();
	/** The target mods built for 1.21.1: their data goes through {@link Legacy1211Data} first. */
	private static final Set<String> ERA_1211 = ConcurrentHashMap.newKeySet();
	/** Weak on both sides: the view's handler holds its pack, so a strong value would keep a closed pack alive across reloads. */
	private static final Map<PackResources, WeakReference<PackResources>> WRAPPED = Collections.synchronizedMap(new WeakHashMap<>());

	/** The report entry that world data packs (ids "file/...") are converted under; null until the boot sets it. */
	private static volatile Report.Entry worldReport;

	private DataShim() { }

	/** Called once at boot: the data packs a world carries are converted like a mod's when they were written for an older data format. */
	public static void worldPacks(Report.Entry report) {
		worldReport = report;
	}

	/** Ids of the world data packs that were converted, for {@link #converted}. */
	private static final Set<String> CONVERTED_WORLD_PACKS = ConcurrentHashMap.newKeySet();

	/** Whether the pack with this id is one Uproot converts: a pack of a target mod, or a world data pack written for an older data format. */
	public static boolean converted(String packId) {
		return TARGETS.containsKey(modOf(packId)) || CONVERTED_WORLD_PACKS.contains(packId);
	}

	/** Whether a data pack of a world declares a data format older than 26.3's (the lowest of min_format, supported_formats and pack_format). */
	private static boolean legacyWorldPack(PackResources pack) {
		if (worldReport == null || !pack.packId().startsWith("file/")) return false;
		try {
			var supplier = pack.getRootResource("pack.mcmeta");
			if (supplier == null) return false;
			try (InputStream in = supplier.get()) {
				Map<String, Object> mcmeta = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				// a file with no pack object, or none of the format keys, says nothing and is not taken for an older pack
				return PackFormat.lowestFormat(mcmeta).orElse(PackFormat.DATA_FORMAT) < PackFormat.DATA_FORMAT;
			}
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Called once at boot with the report entry of each mod Uproot rewrites. */
	public static void target(String modId, Report.Entry report) {
		TARGETS.put(modId, report);
	}

	/** As {@link #target(String, Report.Entry)}; {@code era1211} for a mod built for 1.21.1, whose data starts from the shapes of that release. */
	public static void target(String modId, Report.Entry report, boolean era1211) {
		target(modId, report);
		if (era1211) ERA_1211.add(modId);
	}

	/** The id of the mod a pack belongs to: its own for a pack Fabric API made, else the first part of a "mod/id:name" pack id. */
	private static String modOf(PackResources pack) {
		if (pack instanceof ModPackResources mod) return mod.getFabricModMetadata().getId();
		return modOf(pack.packId());
	}

	private static String modOf(String id) {
		if (id.startsWith("mod/")) id = id.substring("mod/".length());
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(0, colon);
	}

	/** The pack itself, or a view of it when it belongs to a target mod. */
	public static PackResources wrap(PackResources pack) {
		Report.Entry report = TARGETS.get(modOf(pack));
		if (report == null && !isWrapped(pack) && legacyWorldPack(pack)) {
			report = worldReport;
			CONVERTED_WORLD_PACKS.add(pack.packId());
		}
		if (report == null || isWrapped(pack)) return pack;
		synchronized (WRAPPED) {
			WeakReference<PackResources> known = WRAPPED.get(pack);
			PackResources existing = known == null ? null : known.get();
			if (existing != null) return existing;
			PackResources view = view(pack, report);
			WRAPPED.put(pack, new WeakReference<>(view));
			return view;
		}
	}

	/** True for a view this class made, found from its handler rather than by scanning the map. */
	private static boolean isWrapped(PackResources pack) {
		return Proxy.isProxyClass(pack.getClass()) && Proxy.getInvocationHandler(pack) instanceof ViewHandler;
	}

	private static PackResources view(PackResources p, Report.Entry report) {
		boolean era1211 = ERA_1211.contains(modOf(p));
		ConvertedView view = new ConvertedView(p, report, era1211);
		ConvertedAssets assets = new ConvertedAssets(p, report, era1211);
		return (PackResources) Proxy.newProxyInstance(DataShim.class.getClassLoader(), new Class<?>[] {p instanceof ModPackResources ? ModPackResources.class : PackResources.class}, new ViewHandler(p, view, assets));
	}

	private record ViewHandler(PackResources p, ConvertedView view, ConvertedAssets assets) implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
			try {
				Object special = view.intercept(method, args);
				if (special != ConvertedView.PASS) return special;
				special = assets.intercept(method, args);
				if (special != ConvertedView.PASS) return special;
				return method.invoke(p, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		}
	}
}
