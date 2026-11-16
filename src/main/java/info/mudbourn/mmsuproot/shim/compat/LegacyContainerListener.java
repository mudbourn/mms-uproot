package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.world.Container;

/**
 * 1.21.11's {@code net.minecraft.world.ContainerListener}: told when a {@code SimpleContainer} it was added to changed. 26.3 has no class
 * of that name in {@code net.minecraft.world}; only {@code net.minecraft.world.inventory.ContainerListener} remains, the menu slot listener,
 * which is another interface. {@code mixin.SimpleContainerListenersMixin} brings the old behaviour back for this one.
 */
public interface LegacyContainerListener {
	void containerChanged(Container container);
}
