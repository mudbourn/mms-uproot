package info.mudbourn.mmsuproot.shim.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/** Entity and player calls that 26.3 removed or split. */
public final class LegacyEntities {
	private LegacyEntities() { }

	/** {@code EntityType.is(TagKey)}: tags are tested through the type's registry holder now. */
	public static boolean isTagged(EntityType<?> type, TagKey<EntityType<?>> tag) {
		return type.builtInRegistryHolder().is(tag);
	}

	/** {@code Player.displayClientMessage(Component, boolean)}: the flag chose the action bar over chat. */
	public static void displayClientMessage(Player player, Component message, boolean overlay) {
		if (overlay) {
			player.sendOverlayMessage(message);
		} else {
			player.sendSystemMessage(message);
		}
	}
}
