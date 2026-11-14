package info.mudbourn.mmsuproot.remap;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Changes to Minecraft's own classes that mods built for 1.21.11 need and a rename cannot give. */
public final class GamePatches {
	private static final String HOLDER = "net/minecraft/core/Holder";
	private static final String LEGACY_HOLDERS = "info/mudbourn/mmsuproot/shim/compat/LegacyHolders";
	private static final Map<String, Consumer<ClassNode>> PATCHES = Map.of(HOLDER, GamePatches::holder);

	private GamePatches() { }

	/** The internal names of the Minecraft classes that are patched. */
	public static Set<String> classes() {
		return PATCHES.keySet();
	}

	/** The patched bytes of the Minecraft class {@code internal}, given its original bytes. */
	public static byte[] patch(String internal, byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		PATCHES.get(internal).accept(node);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * Holder is sealed to its two kinds in 26.3 and mods implement it (a registry supplier is a holder), and it gained two abstract
	 * methods. It is open again, and the new methods answer through LegacyHolders for an implementation that has no components of its own.
	 */
	private static void holder(ClassNode node) {
		node.permittedSubclasses = null;
		for (MethodNode m : node.methods) {
			boolean components = m.name.equals("components") && m.desc.equals("()Lnet/minecraft/core/component/DataComponentMap;");
			boolean bound = m.name.equals("areComponentsBound") && m.desc.equals("()Z");
			if (!components && !bound) continue;
			m.access &= ~Opcodes.ACC_ABSTRACT;
			m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_HOLDERS, m.name, "(L" + HOLDER + ";)" + m.desc.substring(2), false));
			m.instructions.add(new InsnNode(components ? Opcodes.ARETURN : Opcodes.IRETURN));
		}
	}
}
