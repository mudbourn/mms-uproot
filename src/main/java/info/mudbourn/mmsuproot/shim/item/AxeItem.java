package info.mudbourn.mmsuproot.shim.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * 1.21.11's {@code AxeItem}. 26.3 removed it: an axe is an ordinary item whose properties carry the tool and
 * block transformer components, so this is that, under the old constructor.
 */
public class AxeItem extends Item {
	public AxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {
		super(properties.axe(material, attackDamage, attackSpeed));
	}
}
