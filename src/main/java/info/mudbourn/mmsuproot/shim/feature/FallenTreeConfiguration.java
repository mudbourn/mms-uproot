package info.mudbourn.mmsuproot.shim.feature;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

/** 1.21.11's settings of the fallen tree feature; the fields are public as they were. */
public class FallenTreeConfiguration implements FeatureConfiguration {
	public final BlockStateProvider trunkProvider;
	public final IntProvider logLength;
	public final List<TreeDecorator> stumpDecorators;
	public final List<TreeDecorator> logDecorators;

	public FallenTreeConfiguration(BlockStateProvider trunkProvider, IntProvider logLength, List<TreeDecorator> stumpDecorators, List<TreeDecorator> logDecorators) {
		this.trunkProvider = trunkProvider;
		this.logLength = logLength;
		this.stumpDecorators = stumpDecorators;
		this.logDecorators = logDecorators;
	}

	public BlockStateProvider trunkProvider() {
		return trunkProvider;
	}

	public IntProvider logLength() {
		return logLength;
	}

	public List<TreeDecorator> stumpDecorators() {
		return stumpDecorators;
	}

	public List<TreeDecorator> logDecorators() {
		return logDecorators;
	}

	public static final class FallenTreeConfigurationBuilder {
		private final BlockStateProvider trunkProvider;
		private final IntProvider logLength;
		private List<TreeDecorator> stumpDecorators = new ArrayList<>();
		private List<TreeDecorator> logDecorators = new ArrayList<>();

		public FallenTreeConfigurationBuilder(BlockStateProvider trunkProvider, IntProvider logLength) {
			this.trunkProvider = trunkProvider;
			this.logLength = logLength;
		}

		public FallenTreeConfigurationBuilder stumpDecorators(List<TreeDecorator> decorators) {
			this.stumpDecorators = decorators;
			return this;
		}

		public FallenTreeConfigurationBuilder logDecorators(List<TreeDecorator> decorators) {
			this.logDecorators = decorators;
			return this;
		}

		public FallenTreeConfiguration build() {
			return new FallenTreeConfiguration(trunkProvider, logLength, stumpDecorators, logDecorators);
		}
	}
}
