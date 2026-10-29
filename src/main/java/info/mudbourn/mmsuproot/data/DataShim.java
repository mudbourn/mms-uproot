package info.mudbourn.mmsuproot.data;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import info.mudbourn.mmsuproot.boot.Report;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.minecraft.server.packs.PackResources;

/**
 * Serves the data of target mods in the shapes 26.3 reads, built in memory when the pack is first
 * asked for data and never written anywhere. The mod's own pack is wrapped where Fabric API hands it
 * to the pack repository, so the game's loaders need no change.
 */
public final class DataShim {
	private static final Map<String, Report.Entry> TARGETS = new ConcurrentHashMap<>();
	private static final Map<PackResources, PackResources> WRAPPED = new ConcurrentHashMap<>();

	private DataShim() { }

	/** Called once at boot with the report entry of each mod Uproot rewrites. */
	public static void target(String modId, Report.Entry report) {
		TARGETS.put(modId, report);
	}

	/** The pack itself, or a view of it when it belongs to a target mod. */
	public static PackResources wrap(PackResources pack) {
		if (!(pack instanceof ModPackResources mod)) return pack;
		Report.Entry report = TARGETS.get(mod.getFabricModMetadata().getId());
		if (report == null) return pack;
		return WRAPPED.computeIfAbsent(pack, p -> {
			ConvertedView view = new ConvertedView(mod, report);
			InvocationHandler handler = (proxy, method, args) -> {
				try {
					Object special = view.intercept(method, args);
					if (special != ConvertedView.PASS) return special;
					return method.invoke(mod, args);
				} catch (InvocationTargetException e) {
					throw e.getCause();
				}
			};
			return (PackResources) Proxy.newProxyInstance(DataShim.class.getClassLoader(), new Class<?>[] {ModPackResources.class}, handler);
		});
	}
}
