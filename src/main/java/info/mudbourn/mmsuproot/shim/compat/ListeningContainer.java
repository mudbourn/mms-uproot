package info.mudbourn.mmsuproot.shim.compat;

/** What {@code mixin.SimpleContainerListenersMixin} makes of {@code SimpleContainer}: a container that keeps listeners, as 1.21.11's did. */
public interface ListeningContainer {
	void mms_uproot$addListener(LegacyContainerListener listener);

	void mms_uproot$removeListener(LegacyContainerListener listener);
}
