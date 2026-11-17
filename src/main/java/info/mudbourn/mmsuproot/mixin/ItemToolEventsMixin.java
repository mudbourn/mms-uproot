package info.mudbourn.mmsuproot.mixin;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.datafixers.util.Pair;
import info.mudbourn.mmsuproot.shim.item.ToolEventPoint;
import info.mudbourn.mmsuproot.shim.item.ToolEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every item the two methods that mods' hooks for 1.21.11's axe and hoe were written against (see {@link ToolEvents}). Their bodies
 * have the shape those hooks inject into: the strip method calls {@link ToolEventPoint#stripLog}, which a hook cancels before, and the till
 * method calls {@code Consumer.accept} with the level, position, till entry and player held in locals. Nothing here changes the world; the
 * sound and the new block are the block transformer's. Mixin lets a mod's hook inject into a method that another mixin merged only when its own priority is the higher
 * number, so this one sits below the default 1000.
 */
@Mixin(value = Item.class, priority = 500)
public abstract class ItemToolEventsMixin implements ToolEvents {
	@Unique
	private static final Pair<Predicate<UseOnContext>, Consumer<UseOnContext>> mms_uproot$TILL = Pair.of(context -> true, context -> { });

	@Override
	public Optional<BlockState> evaluateNewBlockState(Level level, BlockPos pos, Player player, BlockState state) {
		ToolEventPoint.stripLog(level, pos);
		return Optional.of(state);
	}

	@Override
	public InteractionResult uprootTillSoil(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos blockPos = context.getClickedPos();
		Pair<Predicate<UseOnContext>, Consumer<UseOnContext>> pair = mms_uproot$TILL;
		Predicate<UseOnContext> predicate = pair.getFirst();
		Consumer<UseOnContext> consumer = pair.getSecond();
		Player player = context.getPlayer();
		consumer.accept(context);
		return InteractionResult.PASS;
	}
}
