package com.as.suspension.instrumenter.asm;

import static com.as.suspension.instrumenter.asm.SearchUtils.searchForOpcodes;

import com.as.suspension.instrumenter.testhelpers.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class SimpleClassNodeTest {

    @Test
    public void mustNotFindAnyJsrInstructions() throws Exception {
        byte[] input = TestUtils.readZipFromResource("JsrExceptionSuspendTest.zip").get("JsrExceptionSuspendTest.class");
        
        ClassReader cr = new ClassReader(input);
        ClassNode classNode = new SimpleClassNode();
        cr.accept(classNode, 0);
        
        for (MethodNode methodNode : classNode.methods) {
            Assert.assertTrue(searchForOpcodes(methodNode.instructions, Opcodes.JSR).isEmpty());
        }
    }
    
}
