package info.mudbourn.mmsuproot.mixin;

import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The material rule context keeps its random state private; a rule source of a 1.21.11 mod needs its seeded randoms. */
@Mixin(MaterialRuleContext.class)
public interface MaterialRuleContextAccess {
	@Accessor("randomState")
	RandomState mms_uproot$randomState();
}
