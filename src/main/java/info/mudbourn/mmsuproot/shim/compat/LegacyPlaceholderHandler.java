package info.mudbourn.mmsuproot.shim.compat;

/**
 * Placeholder API 2.x's {@code PlaceholderHandler}, which 3.x dropped for {@code Placeholder.Handler}. A lambda a mod makes for it is
 * made for this one: the erased method takes the context and the argument and answers the result, all as objects, so the interface
 * needs no build dependency on Placeholder API (a rule in renames.txt gives the lambda this erased shape).
 */
@FunctionalInterface
public interface LegacyPlaceholderHandler {
	Object onPlaceholderRequest(Object context, Object argument);
}
