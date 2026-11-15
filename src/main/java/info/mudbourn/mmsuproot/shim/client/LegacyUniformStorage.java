package info.mudbourn.mmsuproot.shim.client;

import java.nio.ByteBuffer;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;

/**
 * 26.2's {@code DynamicUniformStorage}: a ring of uniform blocks a mod fills one value at a time. 26.3 renamed it
 * {@link DynamicGpuDataStorageMapped} and gave it the buffer usage as a third argument; this answers the old shape on top of it.
 */
public final class LegacyUniformStorage<T extends LegacyUniformStorage.DynamicUniform> implements AutoCloseable {
	/** A uniform value that writes itself into its block; 26.3's {@link DynamicGpuDataStorage.DynamicGpuData}. */
	public interface DynamicUniform extends DynamicGpuDataStorage.DynamicGpuData {
		@Override
		void write(ByteBuffer buffer);
	}

	/** The usage flag of a uniform buffer, as {@code DynamicGpuData} passes it for its transform blocks. */
	private static final int UNIFORM_USAGE = 128;

	private final DynamicGpuDataStorageMapped<T> storage;

	/** {@code new DynamicUniformStorage(label, blockSize, initialCapacity)}. */
	public LegacyUniformStorage(String label, int blockSize, int capacity) {
		this.storage = new DynamicGpuDataStorageMapped<>(label, blockSize, UNIFORM_USAGE, capacity);
	}

	public void endFrame() {
		storage.endFrame();
	}

	public GpuBufferSlice writeUniform(T value) {
		return storage.writeData(value);
	}

	@Override
	public void close() {
		storage.close();
	}
}
