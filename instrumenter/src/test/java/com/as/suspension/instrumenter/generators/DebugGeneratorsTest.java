package com.as.suspension.instrumenter.generators;

import static com.as.suspension.instrumenter.asm.SearchUtils.findMethodsWithName;

import com.as.suspension.instrumenter.testhelpers.TestUtils;
import com.as.suspension.instrumenter.asm.VariableTable;
import com.as.suspension.instrumenter.generators.DebugGenerators.MarkerType;
import static com.as.suspension.instrumenter.generators.DebugGenerators.debugMarker;
import static com.as.suspension.instrumenter.generators.DebugGenerators.debugPrint;
import static com.as.suspension.instrumenter.generators.GenericGenerators.loadStringConst;
import static com.as.suspension.instrumenter.generators.GenericGenerators.merge;
import static com.as.suspension.instrumenter.generators.GenericGenerators.returnVoid;
import static com.as.suspension.instrumenter.testhelpers.TestUtils.createJarAndLoad;

import java.net.URLClassLoader;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class DebugGeneratorsTest {
    private static final String STUB_CLASSNAME = "SimpleStub";
    private static final String STUB_FILENAME = STUB_CLASSNAME + ".class";
    private static final String ZIP_RESOURCE_PATH = STUB_CLASSNAME + ".zip";
    private static final String STUB_METHOD_NAME = "fillMeIn";

    private ClassNode classNode;
    private MethodNode methodNode;
    
    @Before
    public void setUp() throws Exception {
        // Load class, get method
        classNode = TestUtils.readZipResourcesAsClassNodes(ZIP_RESOURCE_PATH).get(STUB_FILENAME);
        methodNode = findMethodsWithName(classNode.methods, STUB_METHOD_NAME).get(0);
    }

    @Test
    public void mustNotCrashOnMarker() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[] { });
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);

        methodNode.instructions
                = merge(
                        // test marker of each type
                        debugMarker(MarkerType.NONE, "marker1"),
                        debugMarker(MarkerType.CONSTANT, "marker2"),
                        debugMarker(MarkerType.STDOUT, "marker3"),
                        returnVoid()
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = TestUtils.createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            MethodUtils.invokeMethod(obj, STUB_METHOD_NAME);
        }
    }

    @Test
    public void mustNotCrashOnDebugPrint() throws Exception {
        // Augment signature
        methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[] { });
        
        // Initialize variable table
        VariableTable varTable = new VariableTable(classNode, methodNode);

        methodNode.instructions
                = merge(
                        // test marker of each type
                        debugPrint(loadStringConst("marker1")),
                        returnVoid()
                );
        
        // Write to JAR file + load up in classloader -- then execute tests
        try (URLClassLoader cl = TestUtils.createJarAndLoad(classNode)) {
            Object obj = cl.loadClass(STUB_CLASSNAME).newInstance();
            MethodUtils.invokeMethod(obj, STUB_METHOD_NAME);
        }
    }
}
