package info.mudbourn.mmsuproot.data;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import info.mudbourn.mmsuproot.boot.Report;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.minecraft.server.packs.PackResources;

/**
 * Serves the data and client assets of target mods in the shapes 26.3 reads, built in memory when the pack is first
 * asked for data and never written anywhere. The mod's own pack is wrapped where Fabric API hands it
 * to the pack repository, so the game's loaders need no change.
 */
public final class DataShim {
	private static final Map<String, Report.Entry> TARGETS = new ConcurrentHashMap<>();
	/** Weak on both sides: the view's handler holds its pack, so a strong value would keep a closed pack alive across reloads. */
	private static final Map<PackResources, WeakReference<PackResources>> WRAPPED = Collections.synchronizedMap(new WeakHashMap<>());

	private DataShim() { }

	/** Called once at boot with the report entry of each mod Uproot rewrites. */
	public static void target(String modId, Report.Entry report) {
		TARGETS.put(modId, report);
	}

	/** The id of the mod a pack belongs to: its own for a pack Fabric API made, else the first part of a "mod/id:name" pack id. */
	private static String modOf(PackResources pack) {
		if (pack instanceof ModPackResources mod) return mod.getFabricModMetadata().getId();
		String id = pack.packId();
		if (id.startsWith("mod/")) id = id.substring("mod/".length());
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(0, colon);
	}

	/** The pack itself, or a view of it when it belongs to a target mod. */
	public static PackResources wrap(PackResources pack) {
		Report.Entry report = TARGETS.get(modOf(pack));
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
		ConvertedView view = new ConvertedView(p, report);
		ConvertedAssets assets = new ConvertedAssets(p, report);
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
