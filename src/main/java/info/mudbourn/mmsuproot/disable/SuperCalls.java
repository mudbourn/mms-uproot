package info.mudbourn.mmsuproot.disable;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Finds, for each constructor, the super (or {@code this}) constructor call it makes, so a stubbed
 * constructor can repeat it with default arguments. A {@code new Super(...)} inside the arguments
 * is told apart from the real call by counting the {@code NEW}s of the same type.
 */
final class SuperCalls {
	private SuperCalls() { }

	/** Keys are {@code <init>} plus the descriptor; values are {owner, descriptor}. */
	static Map<String, String[]> scan(ClassReader reader) {
		Map<String, String[]> out = new HashMap<>();
		String self = reader.getClassName();
		String sup = reader.getSuperName();
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (!name.equals("<init>")) return null;
				return new MethodVisitor(Opcodes.ASM9) {
					private int pendingNew;

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if (opcode == Opcodes.NEW && (type.equals(self) || type.equals(sup))) pendingNew++;
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String insnName, String insnDesc, boolean itf) {
						if (opcode != Opcodes.INVOKESPECIAL || !insnName.equals("<init>")) return;
						if (!owner.equals(self) && !owner.equals(sup)) return;
						if (pendingNew > 0) {
							pendingNew--;
						} else {
							out.putIfAbsent(name + descriptor, new String[] {owner, insnDesc});
						}
					}
				};
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return out;
	}
}
