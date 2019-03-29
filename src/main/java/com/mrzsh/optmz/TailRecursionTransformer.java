package com.mrzsh.optmz;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;

import java.security.ProtectionDomain;
import java.util.*;

import java.util.stream.IntStream;

public class TailRecursionTransformer implements ClassFileTransformer {

    private final Set<Integer> LEGAL_RETURN_OP_CODES =
            new HashSet<>(Arrays.asList(
                    Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN));

    private final String ANNOTATION = Type.getDescriptor(TailRecursion.class);


    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        List<MethodNode> modified = Optional.ofNullable(cn.methods).stream().flatMap(List::stream) // convert to Stream<MethodNode>
                .filter((methodNode) ->
                        Optional.ofNullable(methodNode.visibleAnnotations)
                    .filter((annotationNodes -> annotationNodes.stream().anyMatch((val)-> val.desc.equals(ANNOTATION)))).isPresent()) //filter
                .reduce(new ArrayList<>(),
                        (list, methodNode) -> {
                            coreTemplate(methodNode, cn);
                            list.add(methodNode);
                            return list;
                        },
                        (list1, list2)-> {
                                list1.addAll(list2);
                                return list1;
                        });

        if(!modified.isEmpty()) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return null;
    }

    private void coreTemplate(MethodNode methodNode, ClassNode classNode) {
        ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
        while (it.hasNext()) {
            AbstractInsnNode crtInstruction = it.next();
            if(LEGAL_RETURN_OP_CODES.contains(crtInstruction.getOpcode())) {
                if(checkInvokedSelf(crtInstruction, classNode.name, methodNode.name, methodNode.desc)) {
                    modifyTargetMethod(methodNode, it);
                }
            }
        }
    }

    private boolean checkInvokedSelf(AbstractInsnNode rtnOps, String selfClassName, String methodName, String desc) {
        AbstractInsnNode returnVal = rtnOps.getPrevious();
        if(returnVal.getOpcode() == Opcodes.INVOKESPECIAL
                || returnVal.getOpcode() == Opcodes.INVOKEVIRTUAL
                || returnVal.getOpcode() == Opcodes.INVOKESTATIC) {
            MethodInsnNode methodInsnNode = (MethodInsnNode) returnVal;
            // check is invoke self
            if(selfClassName.equals(methodInsnNode.owner)
                    && methodName.equals(methodInsnNode.name)
                    && desc.equals(methodInsnNode.desc)) {
                return true;
            }
        }
        return false;
    }

    private void modifyTargetMethod(MethodNode methodNode, ListIterator<AbstractInsnNode> it) {
        Type[] types = Type.getArgumentTypes(methodNode.desc);
        // generate store ops
        Arrays.stream(types, 0, types.length)
                .map(TailRecursionTransformer::opsMap)
                .reduce(0,
                        (idx, ops)->{
                            int newIdx = idx + ops[1];
                            it.add(new VarInsnNode(ops[0], types.length - newIdx));
                            return idx + ops[1];
                        },
                        (idx1,idx2)-> Integer.sum(idx1, idx2));

        it.add(new JumpInsnNode(Opcodes.GOTO,
                (LabelNode) methodNode.instructions.getFirst()));
        // go to old ops
        int count = types.length + 2;
        IntStream.range(0,count).forEach((val)-> it.previous());
        // remove old ops
        it.remove();it.previous();it.remove();

    }

    // return[0] : instruction
    // return[1] : width
    private static int[] opsMap(Type p) {
        int code;
        int width = 1;
        if(Type.INT_TYPE.equals(p)
                || Type.SHORT_TYPE.equals(p)
                || Type.BOOLEAN_TYPE.equals(p)
                || Type.CHAR_TYPE.equals(p)) {
            code = Opcodes.ISTORE;
        } else if(Type.LONG_TYPE.equals(p)) {
            code = Opcodes.LSTORE;
            width = 2;
        } else {
            code = Opcodes.ASTORE;
        }
        return new int[]{code, width};
    }


}
