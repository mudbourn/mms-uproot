package info.mudbourn.mmsuproot.shim.client;

import java.util.function.Predicate;

/** What {@code mixin.EditBoxFilterMixin} makes of {@code EditBox}: a box that takes a filter on its text, as 1.21.11's did. */
public interface FilteredEditBox {
	void mms_uproot$setFilter(Predicate<String> filter);
}
