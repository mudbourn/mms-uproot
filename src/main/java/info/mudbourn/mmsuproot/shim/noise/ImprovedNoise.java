package info.mudbourn.mmsuproot.shim.noise;

import net.minecraft.util.RandomSource;

/**
 * The classic improved Perlin noise that 1.21.11 had under this name, written from the published
 * algorithm. 26.3 replaced it with other gradient noise classes of a different shape, so mods that
 * sample it directly are pointed here. The values are not the ones vanilla gave for the same seed.
 */
public final class ImprovedNoise {
	private final byte[] p = new byte[256];
	public final double xo;
	public final double yo;
	public final double zo;

	public ImprovedNoise(RandomSource random) {
		xo = random.nextDouble() * 256.0;
		yo = random.nextDouble() * 256.0;
		zo = random.nextDouble() * 256.0;
		for (int i = 0; i < 256; i++) p[i] = (byte) i;
		for (int i = 0; i < 256; i++) {
			int j = random.nextInt(256 - i);
			byte t = p[i];
			p[i] = p[i + j];
			p[i + j] = t;
		}
	}

	public double noise(double x, double y, double z) {
		x += xo;
		y += yo;
		z += zo;
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		double xf = x - xi;
		double yf = y - yi;
		double zf = z - zi;
		double u = fade(xf);
		double v = fade(yf);
		double w = fade(zf);

		int a = perm(xi) + yi;
		int aa = perm(a) + zi;
		int ab = perm(a + 1) + zi;
		int b = perm(xi + 1) + yi;
		int ba = perm(b) + zi;
		int bb = perm(b + 1) + zi;

		return lerp(w,
				lerp(v, lerp(u, grad(perm(aa), xf, yf, zf), grad(perm(ba), xf - 1, yf, zf)),
						lerp(u, grad(perm(ab), xf, yf - 1, zf), grad(perm(bb), xf - 1, yf - 1, zf))),
				lerp(v, lerp(u, grad(perm(aa + 1), xf, yf, zf - 1), grad(perm(ba + 1), xf - 1, yf, zf - 1)),
						lerp(u, grad(perm(ab + 1), xf, yf - 1, zf - 1), grad(perm(bb + 1), xf - 1, yf - 1, zf - 1))));
	}

	private int perm(int i) {
		return p[i & 255] & 255;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	private static double grad(int hash, double x, double y, double z) {
		int h = hash & 15;
		double u = h < 8 ? x : y;
		double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
		return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
	}
}
