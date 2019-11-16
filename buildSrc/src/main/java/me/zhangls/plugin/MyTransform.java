package me.zhangls.plugin;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * Author: Zhang Lishun
 * Date: 2019/09/12.
 */
public class MyTransform extends Transform {

    @Override
    public String getName() {
        return "ZhanglsTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
//        AbstractTask abstractTask = (AbstractTask) (transformInvocation.getContext());
//        Logger logger = abstractTask.getLogger();

        transformInvocation.getOutputProvider().deleteAll();

        Collection<TransformInput> inputs = transformInvocation.getInputs();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();

        for (TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {

                File destFile = outputProvider.getContentLocation(jarInput.getName(),
                        jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);

                System.out.println(jarInput.getFile().getAbsolutePath()
                        + "->" + destFile.getAbsolutePath());
                transform(jarInput.getFile(), destFile);
//                FileUtils.copyFile(jarInput.getFile(), destFile);
            }

            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                File destFile = outputProvider.getContentLocation(directoryInput.getName(),
                        directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);

                System.out.println(directoryInput.getFile().getAbsolutePath()
                        + "->" + destFile.getAbsolutePath());
                FileUtils.copyDirectoryToDirectory(directoryInput.getFile(), destFile);
//                info(directoryInput.getFile().getAbsolutePath() + "->" + destFile.getAbsolutePath());
            }
        }
    }

    private void transform(File input, File output) throws IOException {
        if (!input.isFile()) {
            return;
        }

        int index = output.getName().lastIndexOf(".") + 1;
        String outputExt = output.getName().substring(index).toLowerCase();

        switch (outputExt) {
            case "jar":
                if (!output.exists()) {
                    output.getParentFile().mkdirs();
                    output.createNewFile();
                }
                JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(output));

                JarFile jarFile = new JarFile(input);
                Enumeration<JarEntry> entries = new JarFile(input).entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();

                    outputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                    if (jarEntry.isDirectory()) {
                        continue;
                    }

                    int jarEntryIndex = jarEntry.getName().lastIndexOf(".");
                    String jarEntryExt = jarEntry.getName().substring(jarEntryIndex + 1);

                    if ("class".equals(jarEntryExt)) {
                        InputStream is = jarFile.getInputStream(jarEntry);
                        ByteArrayInputStream bais = new ByteArrayInputStream(transform(is));
                        copyTo(bais, outputStream);

                        is.close();
                    } else {
                        InputStream is = jarFile.getInputStream(jarEntry);
                        copyTo(is, outputStream);

                        is.close();
                    }
                }
                outputStream.close();
                break;
            case "class":
//                    {
//                    InputStream is = new FileInputStream(input);
//                    copyTo(is,outputStream);
//                    InputStream is = jarFile.getInputStream(jarEntry);
//                    ByteArrayInputStream bais = new ByteArrayInputStream(transform(is));
//                    copyTo(bais, outputStream);
//                }

                break;
            default:
                FileUtils.copyFile(input, output);
                break;
        }
    }

    private Long copyTo(InputStream inputStream, OutputStream outputStream) throws IOException {
        long bytesCopied = 0;
        byte[] buffer = new byte[8 * 1024];
        int bytes = inputStream.read(buffer);
        while (bytes >= 0) {
            outputStream.write(buffer, 0, bytes);
            bytesCopied += bytes;
            bytes = inputStream.read(buffer);
        }
        return bytesCopied;
    }

    private byte[] transform(InputStream inputStream) throws IOException {
        byte[] bytes = readBytes(inputStream);
        return transform(bytes);
    }

    private byte[] readBytes(InputStream inputStream) throws IOException {
        int max = Math.max(8 * 1024, inputStream.available());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(max);
        copyTo(inputStream, buffer);
        return buffer.toByteArray();
    }

    private byte[] transform(byte[] sourceBytes) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassReader classReader = new ClassReader(sourceBytes);

        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        ClassNode newClassNode = transform(classNode);

        newClassNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    private ClassNode transform(ClassNode classNode) {
        if ("com/google/firebase/iid/zzaz".equals(classNode.name)) {
            System.out.println("match com/google/firebase/iid/zzaz");
            classNode.methods.removeIf(new Predicate<MethodNode>() {
                @Override
                public boolean test(MethodNode methodNode) {
                    return "onReceive".equals(methodNode.name);
                }
            });

            onReceive(classNode);
        }
        if ("com/google/android/gms/common/stats/ConnectionTracker".equals(classNode.name)) {
            System.out.println("find com/google/android/gms/common/stats/ConnectionTracker");
            classNode.methods.removeIf(new Predicate<MethodNode>() {
                @Override
                public boolean test(MethodNode methodNode) {
                    return "unbindService".equals(methodNode.name);
                }
            });

            unbindService(classNode);
        }

        return classNode;
    }

    private void unbindService(ClassNode classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "unbindService", "(Landroid/content/Context;Landroid/content/ServiceConnection;)V", null, null);
        {
            AnnotationVisitor annotationVisitor0 = methodVisitor.visitAnnotation("Landroid/annotation/SuppressLint;", false);
            {
                AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("value");
                annotationVisitor1.visit(null, "UntrackedBindService");
                annotationVisitor1.visitEnd();
            }
            annotationVisitor0.visitEnd();
        }
        {
            AnnotationVisitor annotationVisitor0 = methodVisitor.visitAnnotation("Lcom/google/android/gms/common/annotation/KeepForSdk;", false);
            annotationVisitor0.visitEnd();
        }
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(69, label0);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "android/content/Context", "unbindService", "(Landroid/content/ServiceConnection;)V", false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(72, label1);
        Label label3 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label3);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(70, label2);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Exception"});
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label4 = new Label();
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(71, label4);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V", false);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(73, label3);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitInsn(RETURN);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label4, label3, 3);
        methodVisitor.visitLocalVariable("this", "Lcom/google/android/gms/common/stats/ConnectionTracker;", null, label0, label5, 0);
        methodVisitor.visitLocalVariable("var1", "Landroid/content/Context;", null, label0, label5, 1);
        methodVisitor.visitLocalVariable("var2", "Landroid/content/ServiceConnection;", null, label0, label5, 2);
        methodVisitor.visitMaxs(2, 4);
        methodVisitor.visitEnd();
    }

    private void onReceive(ClassNode classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL,
                "onReceive", "(Landroid/content/Context;Landroid/content/Intent;)V",
                null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(33, label3);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(GETFIELD, "com/google/firebase/iid/zzaz",
                "zzdk", "Lcom/google/firebase/iid/zzay;");
        Label label4 = new Label();
        methodVisitor.visitJumpInsn(IFNULL, label4);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(34, label5);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(GETFIELD, "com/google/firebase/iid/zzaz",
                "zzdk", "Lcom/google/firebase/iid/zzay;");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "com/google/firebase/iid/zzay",
                "zzao", "()Z", false);
        methodVisitor.visitJumpInsn(IFEQ, label4);
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(35, label6);
        methodVisitor.visitMethodInsn(INVOKESTATIC,
                "com/google/firebase/iid/FirebaseInstanceId",
                "zzl", "()Z", false);
        Label label7 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label7);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitLineNumber(36, label8);
        methodVisitor.visitLdcInsn("FirebaseInstanceId");
        methodVisitor.visitLdcInsn("Connectivity changed. Starting background sync.");
        methodVisitor.visitMethodInsn(INVOKESTATIC,
                "android/util/Log", "d",
                "(Ljava/lang/String;Ljava/lang/String;)I", false);
        methodVisitor.visitInsn(POP);
        methodVisitor.visitLabel(label7);
        methodVisitor.visitLineNumber(39, label7);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(GETFIELD,
                "com/google/firebase/iid/zzaz",
                "zzdk", "Lcom/google/firebase/iid/zzay;");
        methodVisitor.visitInsn(LCONST_0);
        methodVisitor.visitMethodInsn(INVOKESTATIC,
                "com/google/firebase/iid/FirebaseInstanceId",
                "zza", "(Ljava/lang/Runnable;J)V", false);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(41, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(GETFIELD,
                "com/google/firebase/iid/zzaz",
                "zzdk", "Lcom/google/firebase/iid/zzay;");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                "com/google/firebase/iid/zzay",
                "getContext", "()Landroid/content/Context;", false);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                "android/content/Context",
                "unregisterReceiver",
                "(Landroid/content/BroadcastReceiver;)V", false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(44, label1);
        Label label9 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label9);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(42, label2);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0,
                null, 1, new Object[]{"java/lang/Exception"});
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label10 = new Label();
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(43, label10);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception",
                "printStackTrace", "()V", false);
        methodVisitor.visitLabel(label9);
        methodVisitor.visitLineNumber(45, label9);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitFieldInsn(PUTFIELD, "com/google/firebase/iid/zzaz",
                "zzdk", "Lcom/google/firebase/iid/zzay;");
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(48, label4);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitInsn(RETURN);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLocalVariable("e",
                "Ljava/lang/Exception;", null, label10, label9, 3);
        methodVisitor.visitLocalVariable("this",
                "Lcom/google/firebase/iid/zzaz;", null, label3, label11, 0);
        methodVisitor.visitLocalVariable("var1",
                "Landroid/content/Context;", null, label3, label11, 1);
        methodVisitor.visitLocalVariable("var2",
                "Landroid/content/Intent;", null, label3, label11, 2);
        methodVisitor.visitMaxs(3, 4);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
    }
}
