package info.mudbourn.mmsuproot.shim.client;

/** Values that the handler rules of renames.txt give a parameter the camera of 26.3 has no counterpart for. */
public final class LegacyCamera {
	private LegacyCamera() { }

	/**
	 * What 1.21.11's {@code GameRenderer.getFov} took as "apply the field of view option": true for the world's view, false for the hand's.
	 * {@code Camera.calculateFov} is the world's, so the answer is always true.
	 */
	public static boolean useFovSetting() {
		return true;
	}
}
