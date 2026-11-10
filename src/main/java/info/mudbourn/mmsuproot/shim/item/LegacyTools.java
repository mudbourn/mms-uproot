package info.mudbourn.mmsuproot.shim.item;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-ins for the helpers of 1.21.11's {@code HoeItem} and Fabric's {@code TillableBlockRegistry}.
 * 26.3 has no tool classes: tilling is a block transformer, registered through Fabric's
 * {@link BlockTransformerHelper}. A mod's till callback is recognised when it was built by
 * {@link #changeIntoState} and turned into that registration; anything else cannot be read back.
 */
public final class LegacyTools {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");

	private LegacyTools() { }

	/** {@code obj instanceof AxeItem}: 26.3 has no axe class, an axe is an item of the axes tag. */
	public static boolean isAxe(Object obj) {
		return obj instanceof Item item && item.builtInRegistryHolder().is(ItemTags.AXES);
	}

	/** {@code HoeItem.changeIntoState(BlockState)}: a callback that tills into {@code state}. */
	public static Consumer<UseOnContext> changeIntoState(BlockState state) {
		return new TillConsumer(state);
	}

	/** {@code HoeItem.onlyIfAirAbove}: the block above is air and the hoe was not used from below. */
	public static boolean onlyIfAirAbove(UseOnContext context) {
		BlockPos pos = context.getClickedPos();
		return context.getClickedFace() != Direction.DOWN && context.getLevel().getBlockState(pos.above()).isAir();
	}

	/**
	 * {@code TillableBlockRegistry.register(Block, Predicate, Consumer)}. Only a callback from
	 * {@link #changeIntoState} can be turned into a transformer. The predicate is assumed to be
	 * {@link #onlyIfAirAbove}, which the transformer enforces itself.
	 */
	public static void registerTillable(Block block, Predicate<UseOnContext> predicate, Consumer<UseOnContext> consumer) {
		if (consumer instanceof TillConsumer till) {
			BlockTransformerHelper.registerTilling(block, till.state());
		} else {
			LOGGER.warn("Tilling for {} skipped: only HoeItem.changeIntoState callbacks can be converted to block transformers", block);
		}
	}

	/** The remembered target state of a till callback, plus the old behaviour if something calls it. */
	public record TillConsumer(BlockState state) implements Consumer<UseOnContext> {
		@Override
		public void accept(UseOnContext context) {
			Level level = context.getLevel();
			BlockPos pos = context.getClickedPos();
			level.setBlock(pos, this.state, Block.UPDATE_ALL_IMMEDIATE);
			level.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(context.getPlayer(), this.state));
		}
	}
}
