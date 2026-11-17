package info.mudbourn.mmsuproot.mixin;

import java.util.Iterator;
import java.util.Optional;

import info.mudbourn.mmsuproot.shim.item.ToolEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 1.21.11's axe and hoe each told mods, through their own methods, when a log was about to be stripped and soil to be tilled. 26.3 does both
 * in {@code BlockTransformer.transformBlock}, shared by axes, hoes and shovels, so this tells the held item (see {@link ToolEvents}) at the
 * point where the transform has been chosen and nothing has happened yet: before the advancement trigger, the item's damage and the block change.
 * Which transform it is follows from its sound, the way the game's own data tells them apart: stripping makes the strip sound, tilling the
 * till sound; scraping, waxing off and shovelling are neither. A strip that a hook cancelled ends the use without result, as the old axe did.
 * Tilling was only announced on the server, stripping on both sides.
 */
@Mixin(BlockTransformer.class)
public abstract class BlockTransformerMixin {
	@Inject(method = "transformBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/context/UseOnContext;getPlayer()Lnet/minecraft/world/entity/player/Player;", ordinal = 0), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
	private void mms_uproot$toolEvents(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir, BlockPos pos, Level level, Iterator<?> iterator, BlockTransformer.BlockTransformData data, Direction face, BlockState newState, BlockState updatedState) {
		Item item = context.getItemInHand().getItem();
		if (!(((Object) item) instanceof ToolEvents events)) return;
		boolean strip = data.sound().value() == SoundEvents.AXE_STRIP.value();
		boolean till = data.sound().value() == SoundEvents.HOE_TILL.value();
		if (strip) {
			Player player = context.getPlayer();
			Optional<BlockState> result = events.evaluateNewBlockState(level, pos, player, level.getBlockState(pos));
			if (result.isEmpty()) cir.setReturnValue(InteractionResult.PASS);
		} else if (till && !level.isClientSide()) {
			events.uprootTillSoil(context);
		}
	}
}
