package uk.co.thinkofdeath.vanillacord;

import org.objectweb.asm.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

public abstract class HelperVisitor extends ClassVisitor {
    protected final HashMap<String, Object> values = new HashMap<>();
    private final LinkedHashMap<String, byte[]> queue;

    protected HelperVisitor(LinkedHashMap<String, byte[]> queue, ClassWriter writer) {
        super(Opcodes.ASM9, writer);
        this.queue = queue;
    }

    protected abstract void generate();

    Class<?> getClass(String value) throws ClassNotFoundException {
        return Class.forName(value.substring(0, value.length() - 6));
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        generate();
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(name, outerName, innerName, access);

        try {
            ClassReader classReader = new ClassReader(Main.class.getResourceAsStream('/' + name + ".class"));
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classReader.accept(new ClassVisitor(Opcodes.ASM9, classWriter) {

                @Override // Replace volatile primitive constants with final ones
                public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    if ((access & Opcodes.ACC_FINAL) == 0) {
                        int sort = Type.getType(desc).getSort();
                        if ((Type.ARRAY > sort && sort > Type.VOID)) {
                            Object replacement = values.get("VCCR-" + innerName + '-' + name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1));
                            if (replacement != null) {
                                if ((access & Opcodes.ACC_VOLATILE) != 0) access &= ~Opcodes.ACC_VOLATILE;
                                return super.visitField(access | Opcodes.ACC_FINAL, name, desc, signature, replacement);
                            }
                        }
                    }
                    return super.visitField(access, name, desc, signature, value);
                }

                @Override // Replace direct string constants in <clinit>
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            Object replacement;
                            if (value instanceof String && (replacement = values.get(value)) != null) {
                                super.visitLdcInsn(replacement);
                            } else {
                                super.visitLdcInsn(value);
                            }
                        }
                    };
                }
            }, 0);
            queue.put(name + ".class", classWriter.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}