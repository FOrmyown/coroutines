/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.as.suspension.instrumenter;

import com.as.suspension.instrumenter.testhelpers.TestUtils;
import com.as.suspension.instrumenter.generators.DebugGenerators.MarkerType;

import static com.as.suspension.instrumenter.testhelpers.TestUtils.loadClassesInZipResourceAndInstrument;

import com.as.suspension.user.SuspendableContext;
import com.as.suspension.user.Suspendable;
import com.as.suspension.user.CoroutineRunner;
import com.as.suspension.user.MethodState;
import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.apache.commons.lang3.reflect.ConstructorUtils.invokeConstructor;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.apache.commons.lang3.reflect.MethodUtils.invokeStaticMethod;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class InstrumentationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void mustProperlyExecuteSanityTest() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.SANITY_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.SANITY_TEST);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertFalse(runner.execute());
            assertTrue(runner.execute()); // suspendable finished executing here
            assertFalse(runner.execute());

            assertEquals("abab", builder.toString());
        }
    }

    @Test
    public void mustProperlySuspendInDifferentStackAndLocalsStatesTest() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.DIFFERENT_STATES_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.DIFFERENT_STATES_TEST);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // suspendable finished executing here

            assertEquals("ab", builder.toString());
        }
    }

    @Test
    public void mustProperlySuspendWithVirtualMethods() throws Exception {
        performCountTest(SharedConstants.NORMAL_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }
    
    @Test
    public void mustProperlySuspendWithStaticMethods() throws Exception {
        performCountTest(SharedConstants.STATIC_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithInterfaceMethods() throws Exception {
        performCountTest(SharedConstants.INTERFACE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithRecursiveMethods() throws Exception {
        performCountTest(SharedConstants.RECURSIVE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithInheritedMethods() throws Exception {
        performCountTest(SharedConstants.INHERITANCE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatReturnValues() throws Exception {
        performCountTest(SharedConstants.RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnLongs() throws Exception {
        performCountTest(SharedConstants.LONG_RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithMethodsThatOperateOnDoubles() throws Exception {
        performDoubleCountTest(SharedConstants.DOUBLE_RETURN_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlySuspendWithNullTypeInLocalVariableTable() throws Exception {
        performCountTest(SharedConstants.NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }
    
    @Test
    public void mustProperlySuspendWithBasicTypesInLocalVariableTableAndOperandStack() throws Exception {
        performCountTest(SharedConstants.BASIC_TYPE_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustGracefullyIgnoreWhenContinuationPointDoesNotInvokeOtherContinuationPoints() throws Exception {
        performCountTest(SharedConstants.EMPTY_CONTINUATION_POINT_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    // Mix of many tests in to a single coroutine
    @Test
    public void mustProperlySuspendInNonTrivialCoroutine() throws Exception {
        performCountTest(SharedConstants.COMPLEX_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }


    @Test
    public void mustProperlySuspendInNonTrivialCoroutineWhenDebugModeSet() throws Exception {
        performCountTest(SharedConstants.COMPLEX_TEST, new InstrumentationSettings(MarkerType.CONSTANT, true, true));
    }
    
    @Test
    public void mustProperlyContinueWhenExceptionOccursButIsCaughtBeforeReachingRunner() throws Exception {
        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.EXCEPTION_THEN_CONTINUE_INVOKE_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.EXCEPTION_THEN_CONTINUE_INVOKE_TEST);
            Suspendable suspendable = invokeConstructor(cls);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute());
            
            // There's nothing to check. By virtue of it not failing, we know that it worked. Also, it should print out some messages but we
            // can't check to see what got dumped to stdout.
        }
    }
    
    @Test
    public void mustProperlySuspendWithNullTypeInOperandStackTable() throws Exception {
        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST);
            Suspendable suspendable = invokeConstructor(cls);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertFalse(runner.execute());
            
            // There's nothing to check. By virtue of it not failing, we know that it worked. Also, it should print out null but we can't
            // check to see what got dumped to stdout.
        }
    }
    
    @Test
    public void mustProperlySuspendWithUninitializedLocalVariables() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.UNINITIALIZED_VARIABLE_INVOKE_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.UNINITIALIZED_VARIABLE_INVOKE_TEST);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
        }
    }

    @Test
    public void mustNotFailWithVerifierErrorWhenRunningAsPerOfAnActor() throws Exception {
        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.PEERNETIC_FAILURE_TEST + ".zip")) {
            Class<?> cls = classLoader.loadClass(SharedConstants.PEERNETIC_FAILURE_TEST);
            invokeStaticMethod(cls, "main", new Object[] { new String[0] });
        }
    }
    
    @Test
    public void mustRejectLambdas() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("INVOKEDYNAMIC instructions are not allowed");
        
        performCountTest(SharedConstants.LAMBDA_INVOKE_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustProperlyReportExceptions() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception thrown during execution");
        
        performCountTest(SharedConstants.EXCEPTION_THROW_TEST, new InstrumentationSettings(MarkerType.CONSTANT, false, true));
    }

    @Test
    public void mustHaveResetLoadingStateOnException() throws Exception {
        StringBuilder builder = new StringBuilder();
        SuspendableContext suspendableContext = null;
        boolean hit = false;
        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.EXCEPTION_THROW_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.EXCEPTION_THROW_TEST);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);
            suspendableContext = (SuspendableContext) readField(runner, "suspendableContext", true);
            
            runner.execute();
            runner.execute();
            runner.execute();
            runner.execute();
            runner.execute();
            runner.execute();
        } catch (RuntimeException e) {
            hit = true;
        }

        assertTrue(hit);
        
        MethodState firstPointer = (MethodState) readField(suspendableContext, "firstPointer", true);
        MethodState nextLoadPointer = (MethodState) readField(suspendableContext, "nextLoadPointer", true);
        MethodState nextUnloadPointer = (MethodState) readField(suspendableContext, "nextUnloadPointer", true);
        MethodState firstCutpointPointer = (MethodState) readField(suspendableContext, "firstCutpointPointer", true);
        assertEquals(2, suspendableContext.getSize());
        assertNotNull(suspendableContext.getSaved(0));
        assertNotNull(suspendableContext.getSaved(1));
        assertNotNull(firstPointer);
        assertNotNull(nextLoadPointer);
        assertTrue(firstPointer == nextLoadPointer);
        assertNull(nextUnloadPointer);
        assertNull(firstCutpointPointer);
    }

    private void performCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(testClass);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // suspendable finished executing here
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());

            assertEquals("started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n"
                    + "3\n"
                    + "4\n"
                    + "5\n"
                    + "6\n"
                    + "7\n"
                    + "8\n"
                    + "9\n"
                    + "started\n"
                    + "0\n"
                    + "1\n"
                    + "2\n", builder.toString());
        }
    }

    private void performDoubleCountTest(String testClass, InstrumentationSettings settings) throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(testClass + ".zip", settings)) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(testClass);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // suspendable finished executing here
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());

            assertEquals("started\n"
                    + "0.0\n"
                    + "1.0\n"
                    + "2.0\n"
                    + "3.0\n"
                    + "4.0\n"
                    + "5.0\n"
                    + "6.0\n"
                    + "7.0\n"
                    + "8.0\n"
                    + "9.0\n"
                    + "started\n"
                    + "0.0\n"
                    + "1.0\n"
                    + "2.0\n", builder.toString());
        }
    }

    @Test
    public void mustNotInstrumentConstructors() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Instrumentation of constructors not allowed");

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.CONSTRUCTOR_INVOKE_TEST + ".zip")) {
            // do nothing, exception will occur
        }
    }

    @Test
    public void mustNotDoubleInstrument() throws Exception {
        byte[] classContent =
                TestUtils.readZipFromResource(SharedConstants.COMPLEX_TEST + ".zip").entrySet().stream()
                .filter(x -> x.getKey().endsWith(".class"))
                .map(x -> x.getValue())
                .findAny().get();
        List<File> classpath = TestUtils.getClasspath();
        classpath.addAll(classpath);
        
        Instrumenter instrumenter = new Instrumenter(classpath);
        InstrumentationSettings settings = new InstrumentationSettings(MarkerType.NONE, true, true);
        
        byte[] classInstrumented1stPass = instrumenter.instrument(classContent, settings).getInstrumentedClass();
        byte[] classInstrumented2stPass = instrumenter.instrument(classInstrumented1stPass, settings).getInstrumentedClass();
        
        assertArrayEquals(classInstrumented1stPass, classInstrumented2stPass);
    }

    @Test
    public void mustProperlySuspendInTryCatchFinally() throws Exception {
        StringBuilder builder = new StringBuilder();

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.EXCEPTION_SUSPEND_TEST + ".zip", new InstrumentationSettings(MarkerType.STDOUT, false, true))) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.EXCEPTION_SUSPEND_TEST);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // suspendable finished executing here

            assertEquals(
                    "START\n"
                    + "IN TRY 1\n"
                    + "IN TRY 2\n"
                    + "IN CATCH 1\n"
                    + "IN CATCH 2\n"
                    + "IN FINALLY 1\n"
                    + "IN FINALLY 2\n"
                    + "END\n", builder.toString());
        }
    }

    // This class file in this test's ZIP was specifically compiled with JDK 1.2.2
    @Test
    public void mustProperlySuspendInTryCatchFinallyWithOldJsrInstructions() throws Exception {
        StringBuffer builder = new StringBuffer();

        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.JSR_EXCEPTION_SUSPEND_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.JSR_EXCEPTION_SUSPEND_TEST);
            Suspendable suspendable = invokeConstructor(cls, builder);

            CoroutineRunner runner = new CoroutineRunner(suspendable);

            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertTrue(runner.execute());
            assertFalse(runner.execute()); // suspendable finished executing here

            assertEquals(
                    "START\n"
                    + "IN TRY 1\n"
                    + "IN TRY 2\n"
                    + "IN CATCH 1\n"
                    + "IN CATCH 2\n"
                    + "IN FINALLY 1\n"
                    + "IN FINALLY 2\n"
                    + "END\n", builder.toString());
        }
    }
    
    @Test
    public void mustKeepTrackOfSynchronizedBlocks() throws Exception {
        LinkedList<String> tracker = new LinkedList<>();
        
        // mon1/mon2/mon3 all point to different objects that are logically equivalent but different objects. Tracking should ignore logical
        // equivallence and instead focus on checking to make sure that they references are the same. We don't want to call MONITOREXIT on
        // the wrong object because it .equals() another object in the list of monitors being tracked.
        Object mon1 = new ArrayList<>();
        Object mon2 = new ArrayList<>();
        Object mon3 = new ArrayList<>();

        // All we're testing here is tracking. It's difficult to test to see if monitors were re-entered/exited.
        try (URLClassLoader classLoader = TestUtils.loadClassesInZipResourceAndInstrument(SharedConstants.MONITOR_INVOKE_TEST + ".zip")) {
            Class<Suspendable> cls = (Class<Suspendable>) classLoader.loadClass(SharedConstants.MONITOR_INVOKE_TEST);
            Suspendable suspendable = invokeConstructor(cls, tracker, mon1, mon2, mon3);

            CoroutineRunner runner = new CoroutineRunner(suspendable);
            
            // get suspendableContext object so that we can inspect it and make sure its lockstate is what we expect
            SuspendableContext suspendableContext = (SuspendableContext) readField(runner, "suspendableContext", true);

            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1", "mon2", "mon3", "mon1"), tracker);
            assertArrayEquals(new Object[] { mon1 }, suspendableContext.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { mon2, mon3, mon1 }, suspendableContext.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1", "mon2", "mon3"), tracker);
            assertArrayEquals(new Object[] { mon1 }, suspendableContext.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { mon2, mon3 }, suspendableContext.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1", "mon2"), tracker);
            assertArrayEquals(new Object[] { mon1 }, suspendableContext.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { mon2 }, suspendableContext.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.asList("mon1"), tracker);
            assertArrayEquals(new Object[] { mon1 }, suspendableContext.getSaved(0).getLockState().toArray());
            assertArrayEquals(new Object[] { }, suspendableContext.getSaved(1).getLockState().toArray());
            
            assertTrue(runner.execute());
            assertEquals(Arrays.<String>asList(), tracker);
            assertArrayEquals(new Object[] { }, suspendableContext.getSaved(0).getLockState().toArray());
            
            assertFalse(runner.execute()); // suspendable finished executing here
        }
    }
}
