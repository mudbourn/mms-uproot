package info.mudbourn.mmsuproot.shim.compat;

/** 1.21.11's {@code Tuple}: a mutable pair of values. 26.3 removed it. */
public class LegacyTuple<A, B> {
	private A a;
	private B b;

	public LegacyTuple(A a, B b) {
		this.a = a;
		this.b = b;
	}

	public A getA() {
		return a;
	}

	public void setA(A a) {
		this.a = a;
	}

	public B getB() {
		return b;
	}

	public void setB(B b) {
		this.b = b;
	}
}
