package info.mudbourn.mmsuproot.shim.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * 1.21.11's {@code HoeItem}. 26.3 removed it: a hoe is an ordinary item whose properties carry the tool and
 * block transformer components, so this is that, under the old constructor. Its static helpers are
 * {@link LegacyTools}, reached through callmove rules.
 */
public class HoeItem extends Item {
	public HoeItem(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {
		super(properties.hoe(material, attackDamage, attackSpeed));
	}
}
