package info.mudbourn.mmsuproot.mixin;

import java.util.Objects;
import java.util.function.Predicate;

import info.mudbourn.mmsuproot.shim.client.FilteredEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11's {@code EditBox} held a predicate on its text: {@code setValue}, {@code insertText} and {@code deleteCharsToPos} changed the
 * text only when the predicate accepted the result. 26.3 dropped it. This brings it back for the mods that set one; with none set, the
 * predicate accepts every text, which is how 1.21.11 started out.
 */
@Mixin(EditBox.class)
public abstract class EditBoxFilterMixin implements FilteredEditBox {
	@Unique
	private Predicate<String> mms_uproot$filter = Objects::nonNull;

	@Shadow
	private String value;
	@Shadow
	private int maxLength;
	@Shadow
	private int cursorPos;
	@Shadow
	private int highlightPos;

	@Override
	public void mms_uproot$setFilter(Predicate<String> filter) {
		this.mms_uproot$filter = filter;
	}

	@Inject(method = "setValue", at = @At("HEAD"), cancellable = true)
	private void mms_uproot$filterSetValue(String text, CallbackInfo ci) {
		if (!mms_uproot$filter.test(text)) ci.cancel();
	}

	/** The text that {@code insertText} would make, worked out as it does; the filter sees it before anything changes. */
	@Inject(method = "insertText", at = @At("HEAD"), cancellable = true)
	private void mms_uproot$filterInsert(String input, CallbackInfo ci) {
		int start = Math.min(cursorPos, highlightPos);
		int end = Math.max(cursorPos, highlightPos);
		int room = maxLength - value.length() - (start - end);
		if (room <= 0) return;
		String text = StringUtil.filterText(input);
		if (room < text.length()) {
			if (Character.isHighSurrogate(text.charAt(room - 1))) room--;
			text = text.substring(0, room);
		}
		if (!mms_uproot$filter.test(new StringBuilder(value).replace(start, end, text).toString())) ci.cancel();
	}

	@Inject(method = "deleteCharsToPos", at = @At("HEAD"), cancellable = true)
	private void mms_uproot$filterDelete(int pos, CallbackInfo ci) {
		if (value.isEmpty() || highlightPos != cursorPos) return; // a selection is deleted through insertText
		int start = Math.min(pos, cursorPos);
		int end = Math.max(pos, cursorPos);
		if (start != end && !mms_uproot$filter.test(new StringBuilder(value).delete(start, end).toString())) ci.cancel();
	}
}
