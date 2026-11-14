package info.mudbourn.mmsuproot.shim.compat;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentSource;

/** Fabric API's enchantment events, as 1.21.11's builds registered for them. */
public final class LegacyEnchantments {
	/**
	 * {@code EnchantmentEvents.MODIFY}: its listener method now takes a {@code ResourceSource}, and a listener written for the
	 * {@code EnchantmentSource} one overrides only the old method. This event calls the listeners through that old method, which
	 * a listener of the new shape inherits as a default; it is itself a listener of the real event.
	 */
	public static final Event<EnchantmentEvents.Modify> MODIFY = EventFactory.createArrayBacked(EnchantmentEvents.Modify.class,
		listeners -> (key, builder, source) -> {
			for (EnchantmentEvents.Modify listener : listeners) listener.modify(key, builder, EnchantmentSource.fromResourceSource(source));
		});

	static {
		EnchantmentEvents.MODIFY.register(MODIFY.invoker());
	}

	private LegacyEnchantments() { }
}
