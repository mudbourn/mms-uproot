package info.mudbourn.mmsuproot.shim;

/**
 * Target of callmove rules for calls that are dropped: the called class cannot even load on 26.3, so the call itself is
 * rerouted here and the class is never touched.
 */
public final class Skipped {
	private Skipped() {
	}

	public static void call() {
	}
}
