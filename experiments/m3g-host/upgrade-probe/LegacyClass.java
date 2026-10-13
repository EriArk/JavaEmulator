/* SPDX-License-Identifier: Apache-2.0 */
import java.nio.file.*;
import org.objectweb.asm.*;

/** Retargets this Java-5-compatible fixture for the unpatched beta.2 importer. */
public final class LegacyClass {
    public static void main(String[] args) throws Exception {
        try (java.util.stream.Stream<Path> files = Files.list(Paths.get(args[0]))) {
            for (Path path : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                ClassWriter writer = new ClassWriter(0);
                new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public void visit(int v, int a, String n, String s, String p, String[] i) {
                        super.visit(Opcodes.V1_5, a, n, s, p, i);
                    }
                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(a, n, d, s, e)) {
                            @Override public void visitInvokeDynamicInsn(String n, String d, Handle h, Object... b) {
                                throw new IllegalArgumentException("Not a legacy-compatible fixture");
                            }
                        };
                    }
                }, ClassReader.SKIP_FRAMES);
                Files.write(path, writer.toByteArray());
            }
        }
    }
}
