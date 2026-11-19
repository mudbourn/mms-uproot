package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.attribute.modifier.ColorModifier;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/**
 * Environment attribute values of a 26.1 or 26.2 build. Colours were packed ints there; 26.3 holds them as vectors (an RGB modifier
 * takes a {@link Vector3fc}, an ARGB one a {@link Vector4fc}, and the colour attributes' values are vectors too). A packed int that
 * reaches the builder is turned into the vector the attribute or modifier now takes; every other value passes unchanged.
 */
public final class LegacyAttributes {
	private LegacyAttributes() { }

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static EnvironmentAttributeMap.Builder modify(EnvironmentAttributeMap.Builder builder, EnvironmentAttribute attribute, AttributeModifier modifier, Object argument) {
		if (argument instanceof Integer packed) {
			if (modifier instanceof ColorModifier.RgbModifier<?>) argument = ARGB.vector3fFromRGB24(packed);
			else if (modifier instanceof ColorModifier.ArgbModifier<?>) argument = ARGB.vector4fFromARGB32(packed);
		}
		return builder.modify(attribute, modifier, argument);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static EnvironmentAttributeMap.Builder set(EnvironmentAttributeMap.Builder builder, EnvironmentAttribute attribute, Object value) {
		if (value instanceof Integer packed) {
			Object standard = attribute.defaultValue();
			if (standard instanceof Vector3fc) value = ARGB.vector3fFromRGB24(packed);
			else if (standard instanceof Vector4fc) value = ARGB.vector4fFromARGB32(packed);
		}
		return builder.set(attribute, value);
	}
}
