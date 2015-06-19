package org.jcoro.analyzer;

import org.jcoro.MethodId;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author elwood
 */
public class Analyzer {
    /**
     * ���������� { className -> { methodNameToInstrument -> list [ callsToInstrument ] } }
     * ����� �������, ����� ���������, ����� �� ����������������� ����� ������, ���� �������
     *  map.get(className).containsKey(methodName)
     * � ����� �������� ������ ������� ������ ������, ������� ����� �������� � ������ yield(), ����� ���������
     *  map.get(className).get(methodName)
     * ���� ������ ������ ��� �� ������ ������, ������� �� ��� �������� � ������ yield(), �� ���� �����
     * ����������������� �� �����, � ��� �� ����� �� ��������� ���� (����� �������, �� ��������� ���� �� ������
     * ���� �� ������ entry � ������ ������� � �������� ��������).
     */
    public Map<String, Map<String, List<MethodId>>> analyzeJars(List<String> jarPaths) throws IOException {
        // ������ ������ ���������
        for (String jarPath : jarPaths) {
            JarFile jarFile = new JarFile(new File(jarPath));
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (jarEntry.getName().contains("org/jcoro/tests/simpletest/") && jarEntry.getName().contains(".class")) {
                    byte[] bytes = new byte[(int) jarEntry.getSize()];
                    try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                        int totalReaded = 0;
                        while (totalReaded < bytes.length) {
                            int readed = inputStream.read(bytes);
                            if (readed == -1) throw new RuntimeException("Can't read jar entry");
                            totalReaded += readed;
                        }
                    }
                    analyzeFirst(bytes);
                }
            }
        }

        // ������ ������
//        for (String jarPath : jarPaths) {
//            JarFile jarFile = new JarFile(new File(jarPath));
//            Enumeration<JarEntry> jarEntries = jarFile.entries();
//            while (jarEntries.hasMoreElements()) {
//                JarEntry jarEntry = jarEntries.nextElement();
//                if (jarEntry.getName().contains("Test")) {
//                    byte[] bytes = new byte[(int) jarEntry.getSize()];
//                    try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
//                        int totalReaded = 0;
//                        while (totalReaded < bytes.length) {
//                            int readed = inputStream.read(bytes);
//                            if (readed == -1) throw new RuntimeException("Can't read jar entry");
//                            totalReaded += readed;
//                        }
//                    }
//                    analyzeClass(bytes);
//                }
//            }
//        }
        return null;
    }

    private Map<String, String> superNames = new HashMap<>();
    private Map<String, Set<String>> implementingInterfaces = new HashMap<>();
    private Map<String, List<MethodId>> declaredMethods = new HashMap<>();

    private void analyzeFirst(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
            }
        };
        reader.accept(classVisitor, 0);
        System.out.println(classVisitor.hashCode());
    }

    // ������ ������ ��������� - ��� ����� ���������� �� ��������� ������������ � ���������� �����������
    // ��� ������� ������ ���� ����� ����������
    // 1. ������ �������, ����������� (����������������) ��� ����������� (���� ���������) ���� �����
    //    �.�. ������ ���� ������� ���� �� ��������
    // 2. ������ �������, ������� ���������������� ���� �������
    //    �.�. ������ ������� ����� �� ��������

    // ��������:
    // 1. �������� �� �������, ������� � ������� ������ �������. �������� ��������� ������, ������������:
    //    - ������ ���� �������, ����������� ICoroRunnable.run()
    //    - ������ ���� �������, ������� ������ �������� ����� yield()
    //    - ��� ������� ������ - ������ �������, ������� ����� ���� ������� ��� ����������
    // 2. ��� ������� �� �������, ���������� ������ ����� yield(), ��������� ��� ������ ����� �� ����� ������,
    //    �� ��� ���, ���� �� ���������� �����, ����������� ICoroRunnable.run(). ������ �� ���������� �������
    //    ���������� �������, ������� ���������� ����������������� (methodNameToInstrument). � �����(�), �����
    //    ������� ��� ������ ���� � ����������� ������, ���������� ������� �������������� (callsToInstrument).
    // 3. ������� ��� ������, ������� ���������� �����������������, � �������� �� � �������������� ����.

    private Set<MethodId> coroRunnables = new HashSet<>();
    private Map<MethodId, Set<MethodId>> calls = new HashMap<>();
    private Set<MethodId> yieldingMethods = new HashSet<>();
    private Map<MethodId, Set<MethodId>> interfaceImplementations = new HashMap<>();
    private Map<MethodId, Set<MethodId>> overrides = new HashMap<>();

    private void analyzeClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5) {
            private String className;
            private boolean implementsICoroRunnable = false;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                // ��������� ��� ������ � field, � ����� ����������, ��������� �� ���� ����� ICoroRunnable
                className = name;
                for (String i : interfaces) {
                    if ("org/jcoro/ICoroRunnable".equals(i)) {
                        implementsICoroRunnable = true;
                        break;
                    }
                }

                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodId methodId = new MethodId(className, name, desc);

                // ���� ���������� �����, ����������� ICoroRunnable.run()
                if (implementsICoroRunnable && "run".equals(name) && "()V".equals(desc)) {
                    coroRunnables.add(methodId);
                }

                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        // ���� ����� ����� Coro.yield(), ��������� ���������� ����� �� ��������� yieldingMethods
                        if ("org/jcoro/Coro".equals(owner) && "yield".equals(name) && "()V".equals(desc)) {
                            yieldingMethods.add(methodId);
                        } else {
                            // ����� ������ ��������� �� ��������� ���������� �������
                            MethodId callingMethodId = new MethodId(owner, name, desc);
                            Set<MethodId> callsList = calls.get(methodId);
                            if (callsList == null) {
                                callsList = new HashSet<>();
                                calls.put(methodId, callsList);
                            }
                            callsList.add(callingMethodId);
                        }

                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                        // �����, ������� ����� ������ ��� ������ ������
                        Handle callingMethodHandle = (Handle) bsmArgs[1];
                        String callingMethodClassName = callingMethodHandle.getOwner(); // ��� ������, ����������� �����
                        String callingMethodName = callingMethodHandle.getName(); // ��� ������
                        String callingMethodSignature = callingMethodHandle.getDesc(); // ��������� ������

                        // ���� ������ ��������� ICoroRunnable.run(), ��������� �����, � �����������, � coroRunnables
                        if ("run".equals(name) && "()Lorg/jcoro/ICoroRunnable;".equals(desc)) {
                            coroRunnables.add(new MethodId(callingMethodClassName, callingMethodName, callingMethodSignature));
                        }

                        // ��������� ������ � calls, �.�. ��� ����� ���� �������, ���� ������� � ������ ����� ������
                        MethodId callingMethodId = new MethodId(callingMethodClassName, callingMethodName, callingMethodSignature);
                        Set<MethodId> callsList = calls.get(methodId);
                        if (callsList == null) {
                            callsList = new HashSet<>();
                            calls.put(methodId, callsList);
                        }
                        callsList.add(callingMethodId);

                        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
                    }
                };
            }
        };
        reader.accept(classVisitor, 0);
        System.out.println(classVisitor.hashCode());
    }
}
