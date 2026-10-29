package info.mudbourn.mmsuproot.shim.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * 1.21.11's {@code SignItem}. 26.3 removed it and builds sign items from a standing and wall block
 * item whose properties carry empty sign text, so this is that, under the old constructor.
 */
public class SignItem extends StandingAndWallBlockItem {
	public SignItem(Block standing, Block wall, Item.Properties properties) {
		super(standing, wall, Direction.DOWN, properties.signText());
	}
}
