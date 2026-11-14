package info.mudbourn.mmsuproot.shim.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * 1.21.11's {@code ShovelItem}. 26.3 removed it: a shovel is an ordinary item whose properties carry the tool and
 * block transformer components, so this is that, under the old constructor.
 */
public class ShovelItem extends Item {
	public ShovelItem(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {
		super(properties.shovel(material, attackDamage, attackSpeed));
	}
}
