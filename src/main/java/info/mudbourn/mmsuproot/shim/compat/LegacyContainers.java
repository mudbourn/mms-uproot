package info.mudbourn.mmsuproot.shim.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

/** Container and bundle calls of 1.21.11 that 26.3 answers in another shape. */
public final class LegacyContainers {
	private LegacyContainers() { }

	/** {@code SimpleContainer.addListener(listener)}. */
	public static void addListener(SimpleContainer container, LegacyContainerListener listener) {
		((ListeningContainer) container).mms_uproot$addListener(listener);
	}

	/** {@code SimpleContainer.removeListener(listener)}. */
	public static void removeListener(SimpleContainer container, LegacyContainerListener listener) {
		((ListeningContainer) container).mms_uproot$removeListener(listener);
	}

	/**
	 * The arguments of the private {@code BundleContents.Mutable} constructor for {@code new BundleContents.Mutable(contents)}: a copy of the
	 * stacks, the weight and the selected slot, which is what {@code asMutable()} hands it. A bundle whose weight is not valid gives an empty editor,
	 * as {@code asMutable()} does.
	 */
	public static Object[] bundleMutableArgs(BundleContents contents) {
		if (contents.weight().isError()) return new Object[] {new ArrayList<ItemStack>(), Fraction.ZERO, -1};
		List<ItemStack> stacks = new ArrayList<>();
		contents.itemCopies().forEach(stacks::add);
		return new Object[] {stacks, contents.weight().getOrThrow(), contents.getSelectedItemIndex()};
	}
}
