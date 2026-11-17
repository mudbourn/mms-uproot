package info.mudbourn.mmsuproot.shim.item;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What every item answers, through {@code ItemToolEventsMixin}, for the two events that 1.21.11's axe and hoe classes raised
 * and that mods hook: a log about to be stripped and soil about to be tilled. 26.3 does both in the block transformer item
 * component, so {@code BlockTransformerMixin} calls these two methods at the point where it is about to do either, and a hook
 * of a mod that was written for the old methods runs inside them (see the {@code mixintarget} rules in renames.txt).
 * The methods change nothing themselves.
 */
public interface ToolEvents {
	/** The stripping step of 1.21.11's {@code AxeItem.evaluateNewBlockState}: empty when a hook cancelled the strip, the state it would make otherwise. */
	Optional<BlockState> evaluateNewBlockState(Level level, BlockPos pos, Player player, BlockState state);

	/** The point of 1.21.11's {@code HoeItem.useOn} where the till callback ran. */
	InteractionResult uprootTillSoil(UseOnContext context);
}
