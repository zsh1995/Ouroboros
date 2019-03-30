package com.mrzsh.optmz;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;


import java.io.*;

import java.lang.instrument.ClassFileTransformer;

import java.security.ProtectionDomain;
import java.util.*;

import java.util.stream.IntStream;

public class TailRecursionTransformer implements ClassFileTransformer {

    boolean debug;

    private static final String FILE_SEPARATOR = System.getProperty("file.separator");

    private static final PrintStream NOWHERE = new PrintStream(PrintStream.nullOutputStream());

    private PrintStream log = NOWHERE;

    private File output = new File("output");


    public static class Builder {
        boolean debug;
        private TailRecursionTransformer hold;
        public Builder(boolean debug) {
            this.debug = debug;
            hold = new TailRecursionTransformer();
            hold.debug = debug;
        }

        public Builder log(String logPath) {
            Objects.requireNonNull(logPath);
            try {
                if(!logPath.isEmpty()) {
                    hold.log = new PrintStream(logPath);
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException("can't create log file");
            }
            return this;
        }

        public Builder outputModifiedClass(String path) {
            Objects.requireNonNull(path);
            hold.output = new File(path);
            hold.output.mkdir();
            return this;
        }

        public TailRecursionTransformer build() {
            return hold;
        }
    }

    private TailRecursionTransformer(){
        output.mkdir();
        try {
            log = new PrintStream("debug.log");
        } catch (FileNotFoundException e) {
            e.printStackTrace(System.err);
        }
    }



    private final Set<Integer> LEGAL_RETURN_OP_CODES =
            new HashSet<>(Arrays.asList(
                    Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN));

    private final String ANNOTATION = Type.getDescriptor(TailRecursion.class);


    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        byte[] modifiedArr = null;
        try {
            modifiedArr = scanAndModify(className, classfileBuffer);
        } catch (Throwable throwable) {
            throwable.printStackTrace(log);
        }
        return modifiedArr;
    }

    private byte[] scanAndModify(String className, byte[] classfileBuffer) {
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        List<MethodNode> modified = Optional.ofNullable(cn.methods).stream().flatMap(List::stream) // convert to Stream<MethodNode>
                .filter((methodNode) ->
                        Optional.ofNullable(methodNode.visibleAnnotations)
                                .filter((annotationNodes -> annotationNodes.stream().anyMatch((val)-> val.desc.equals(ANNOTATION)))).isPresent()) //filter
                .reduce(new ArrayList<>(),
                        (list, methodNode) -> {
                            list.add(methodNode);
                            coreTemplate(methodNode, cn);
                            return list;
                        },
                        (list1, list2)-> {
                            list1.addAll(list2);
                            return list1;
                        });
        if(!modified.isEmpty()) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            byte[] res = cw.toByteArray();
            if(debug)
                storeModifiedClass(className, classfileBuffer);
            return res;
        }
        return null;
    }

    private void storeModifiedClass(String className, byte[] classfileBuffer) {
        try {
            File file = new File(output, className+".class");
            file.createNewFile();
            new FileOutputStream(file)
                    .write(classfileBuffer);
        } catch (IOException e) {
            e.printStackTrace(log);
        }
    }

    private void coreTemplate(MethodNode methodNode, ClassNode classNode) {
        ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
        while (it.hasNext()) {
            AbstractInsnNode crtInstruction = it.next();
            if(LEGAL_RETURN_OP_CODES.contains(crtInstruction.getOpcode())) {
                if(checkInvokedSelf(crtInstruction, classNode.name, methodNode.name, methodNode.desc)) {
                    modifyTargetMethod(methodNode, it);
                } else {
                    log.println("this method isn't tail recursion");
                }
            }
        }
    }

    private boolean checkInvokedSelf(AbstractInsnNode rtnOps, String selfClassName, String methodName, String desc) {
        AbstractInsnNode invokeOps = rtnOps.getPrevious();
        if(invokeOps.getOpcode() == Opcodes.INVOKESPECIAL
                || invokeOps.getOpcode() == Opcodes.INVOKEVIRTUAL
                || invokeOps.getOpcode() == Opcodes.INVOKESTATIC) {
            MethodInsnNode methodInsnNode = (MethodInsnNode) invokeOps;
            // check is invoke self
            if(selfClassName.equals(methodInsnNode.owner)
                    && methodName.equals(methodInsnNode.name)
                    && desc.equals(methodInsnNode.desc)) {
                return true;
            } else {
                log.println("invoke other method");
            }
        } else {
            log.println("unsuport method " + invokeOps.getOpcode());
        }
        return false;
    }

    private void modifyTargetMethod(MethodNode methodNode, ListIterator<AbstractInsnNode> it) {
        Type[] types = Type.getArgumentTypes(methodNode.desc);
        int argSize = getArgSize(methodNode.desc);
        log.println("argument size :" + (getArgSize(methodNode.desc)));
        log.println("local size : " + methodNode.maxLocals);
        log.println("argument nums : " + types.length);
        // generate store ops
        IntStream.range(0, types.length)
                .mapToObj((i)-> opsMap(types[types.length - i - 1]))
                .reduce(0,
                        (idx, ops)->{
                            int newIdx = idx + ops[1];
                            log.println("newIdx = " + newIdx);
                            it.add(new VarInsnNode(ops[0], argSize - newIdx));
                            return newIdx;
                        },
                        (idx1,idx2)-> Integer.sum(idx1, idx2));
        // if the method is not static, should pop `this`
        if(!checkStatic(methodNode.access)) {
            it.add(new VarInsnNode(Opcodes.ASTORE, 0));
        }

        it.add(new JumpInsnNode(Opcodes.GOTO,
                (LabelNode) methodNode.instructions.getFirst()));
        // go to old ops
        int count = argSize + 2;
        IntStream.range(0,count).forEach((val)-> it.previous());
        // remove old ops
        it.remove();it.previous();it.remove();

    }

    private static int getArgSize(String methodDesc) {
        return Type.getArgumentsAndReturnSizes(methodDesc) >> 2;
    }

    private static boolean checkStatic(int access) {
        return (access & Opcodes.ACC_STATIC ) != 0;
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
        } else if(Type.DOUBLE_TYPE.equals(p)) {
            code = Opcodes.DSTORE;
            width = 2;
        } else if(Type.FLOAT_TYPE.equals(p)) {
            code = Opcodes.FSTORE;
        } else {
            code = Opcodes.ASTORE;
        }
        return new int[]{code, width};
    }


}
