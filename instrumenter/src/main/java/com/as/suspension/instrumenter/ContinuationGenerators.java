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

import static com.as.suspension.instrumenter.SynchronizationGenerators.enterStoredMonitors;
import static com.as.suspension.instrumenter.SynchronizationGenerators.exitStoredMonitors;
import static com.as.suspension.instrumenter.generators.GenericGenerators.call;

import com.as.suspension.instrumenter.asm.MethodInvokeUtils;
import com.as.suspension.instrumenter.asm.VariableTable;
import com.as.suspension.instrumenter.generators.DebugGenerators;
import com.as.suspension.instrumenter.generators.GenericGenerators;
import com.as.suspension.user.SuspendableContext;

import static com.as.suspension.user.SuspendableContext.MODE_NORMAL;
import static com.as.suspension.user.SuspendableContext.MODE_SAVING;
import com.as.suspension.user.LockState;
import com.as.suspension.user.MethodState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.stream.IntStream;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import static com.as.suspension.instrumenter.SynchronizationGenerators.createMonitorContainer;
import static com.as.suspension.instrumenter.OperandStackStateGenerators.loadOperandStack;
import static com.as.suspension.instrumenter.OperandStackStateGenerators.saveOperandStack;
import static com.as.suspension.instrumenter.generators.GenericGenerators.pop;

final class ContinuationGenerators {
    
    private static final Method CONTINUATION_GETMODE_METHOD
            = MethodUtils.getAccessibleMethod(SuspendableContext.class, "getMode");
    private static final Method CONTINUATION_SETMODE_METHOD
            = MethodUtils.getAccessibleMethod(SuspendableContext.class, "setMode", Integer.TYPE);

    // Need a primer on how to handle method states with the SuspendableContext class? There are comments in the SuspendableContext class that describe
    // how things should work.
    private static final Method CONTINUATION_LOADNEXTMETHODSTATE_METHOD
            = MethodUtils.getAccessibleMethod(SuspendableContext.class, "loadNextMethodState");
    private static final Method CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD
            = MethodUtils.getAccessibleMethod(SuspendableContext.class, "unloadCurrentMethodState");
    private static final Method CONTINUATION_UNLOADMETHODSTATETOBEFORE_METHOD
            = MethodUtils.getAccessibleMethod(SuspendableContext.class, "unloadMethodStateToBefore", MethodState.class);
    private static final Method CONTINUATION_PUSHNEWMETHODSTATE_METHOD
            = MethodUtils.getAccessibleMethod(SuspendableContext.class, "pushNewMethodState", MethodState.class);

    private static final Constructor<MethodState> METHODSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(MethodState.class, String.class, Integer.TYPE, Integer.TYPE,
                    Object[].class, LockState.class);
    private static final Method METHODSTATE_GETCONTINUATIONPOINT_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getContinuationPoint");
    private static final Method METHODSTATE_GETDATA_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getData");
    private static final Method METHODSTATE_GETLOCKSTATE_METHOD
            = MethodUtils.getAccessibleMethod(MethodState.class, "getLockState");
    
    private ContinuationGenerators() {
        // do nothing
    }
    
    public static InsnList entryPointLoader(MethodAttributes attrs) {
        Validate.notNull(attrs);

        VariableTable.Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        VariableTable.Variable methodStateVar = attrs.getCoreVariables().getMethodStateVar();
        VariableTable.Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        LockVariables lockVars = attrs.getLockVariables();
        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();

        int numOfContinuationPoints = attrs.getContinuationPoints().size();

        DebugGenerators.MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
        LabelNode startOfMethodLabelNode = new LabelNode();
        return GenericGenerators.merge(
                GenericGenerators.tableSwitch(
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Getting state for switch"),
                                GenericGenerators.call(CONTINUATION_GETMODE_METHOD, GenericGenerators.loadVar(contArg))
                        ),
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Unrecognized state"),
                                GenericGenerators.throwRuntimeException("Unrecognized state")
                        ),
                        0,
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Case 0 -- Fresh invocation"),
                                // create lockstate if method actually has monitorenter/exit in it (var != null if this were the case)
                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[] {
                                        DebugGenerators.debugMarker(markerType, "Creating monitors container"),
                                        createMonitorContainer(markerType, lockVars),
                                }),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Jump to start of method point"),
                                GenericGenerators.jumpTo(startOfMethodLabelNode)
                        ),
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Case 1 -- Saving state"),
                                GenericGenerators.throwRuntimeException("Unexpected state (saving not allowed at this point)")
                        ),
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Case 2 -- Loading state"),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Loading method state"),
                                GenericGenerators.call(CONTINUATION_LOADNEXTMETHODSTATE_METHOD, GenericGenerators.loadVar(contArg)),
                                GenericGenerators.saveVar(methodStateVar),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Getting method state data"),
                                GenericGenerators.call(METHODSTATE_GETDATA_METHOD, GenericGenerators.loadVar(methodStateVar)),
                                GenericGenerators.saveVar(storageContainerVar),
                                // get lockstate if method actually has monitorenter/exit in it (var != null if this were the case)
                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[] {
                                        DebugGenerators.debugMarker(markerType, dbgSig + "Method has synch points, so loading lockstate as well"),
                                        GenericGenerators.call(METHODSTATE_GETLOCKSTATE_METHOD, GenericGenerators.loadVar(methodStateVar)),
                                        GenericGenerators.saveVar(lockStateVar)
                                }),
                                GenericGenerators.tableSwitch(
                                        GenericGenerators.merge(
                                                DebugGenerators.debugMarker(markerType, dbgSig + "Getting continuation id for switch"),
                                                GenericGenerators.call(METHODSTATE_GETCONTINUATIONPOINT_METHOD, GenericGenerators.loadVar(methodStateVar))
                                        ),
                                        GenericGenerators.merge(
                                                DebugGenerators.debugMarker(markerType, dbgSig + "Unrecognized continuation id"),
                                                GenericGenerators.throwRuntimeException("Unrecognized continuation id")
                                        ),
                                        0,
                                        IntStream.range(0, numOfContinuationPoints)
                                                .mapToObj(idx -> restoreState(attrs, idx))
                                                .toArray((x) -> new InsnList[x])
                                )
                                // jump to not required here, switch above either throws exception or jumps to restore point
                        )
                ),
                GenericGenerators.addLabel(startOfMethodLabelNode),
                DebugGenerators.debugMarker(markerType, dbgSig + "Starting method...")
        );
    }




    public static InsnList restoreState(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        ContinuationPoint continuationPoint = InternalUtils.validateAndGetContinuationPoint(attrs, idx, ContinuationPoint.class);
                
        InsnList restoreInsnList;
        if (continuationPoint instanceof SuspendContinuationPoint) {
            restoreInsnList = restoreStateFromSuspend(attrs, idx);
        } else if (continuationPoint instanceof NormalInvokeContinuationPoint) {
            restoreInsnList = restoreStateFromNormalInvocation(attrs, idx);
        } else if (continuationPoint instanceof TryCatchInvokeContinuationPoint) {
            restoreInsnList = restoreStateFromInvocationWithinTryCatch(attrs, idx);
        } else {
            throw new IllegalArgumentException(); // should never happen
        }
        
        return restoreInsnList;
    }
    
    private static InsnList restoreStateFromSuspend(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        SuspendContinuationPoint cp = InternalUtils.validateAndGetContinuationPoint(attrs, idx, SuspendContinuationPoint.class);

        Integer lineNumber = cp.getLineNumber();

        VariableTable.Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        
        VariableTable.Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        LockVariables lockVars = attrs.getLockVariables();
        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        
        Frame<BasicValue> frame = cp.getFrame();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        DebugGenerators.MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
        //          enterLocks(lockState);
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          continuation.setMode(MODE_NORMAL);
        //          goto restorePoint_<number>_continue;
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring SUSPEND " + idx),
                DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking operand stack storage variables"),
                PackStateGenerators.unpackOperandStackStorageArrays(markerType, frame, storageContainerVar, savedStackVars),
                DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking locals storage variables"),
                PackStateGenerators.unpackLocalsStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring operand stack"),
                loadOperandStack(markerType, savedStackVars, frame),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring locals"),
                LocalsStateGenerators.loadLocals(markerType, savedLocalsVars, frame),
                GenericGenerators.mergeIf(lineNumber != null, () -> new Object[] {
                    // We add the line number AFTER locals have been restored, so if you put in a break point at the specified line number
                    // the local vars will all show up.
                    GenericGenerators.lineNumber(lineNumber)
                }),
                // attempt to enter monitors only if method has monitorenter/exit in it (var != null if this were the case)
                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                        DebugGenerators.debugMarker(markerType, dbgSig + "Entering monitors"),
                        enterStoredMonitors(markerType, lockVars),
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Popping off continuation object from operand stack"),
                GenericGenerators.pop(), // frame at the time of invocation to SuspendableContext.suspend() has SuspendableContext reference on the
                       // stack that would have been consumed by that invocation... since we're removing that call, we
                       // also need to pop the SuspendableContext reference from the stack... it's important that we
                       // explicitly do it at this point becuase during loading the stack will be restored with top
                       // of stack pointing to that continuation object
                DebugGenerators.debugMarker(markerType, dbgSig + "Setting mode to normal"),
                GenericGenerators.call(CONTINUATION_SETMODE_METHOD, GenericGenerators.loadVar(contArg), GenericGenerators.loadIntConst(MODE_NORMAL)),
                // We've successfully completed our restore and we're continuing the invocation, so we need "discard" this method state
                DebugGenerators.debugMarker(markerType, dbgSig + "Discarding saved method state"),
                GenericGenerators.call(CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD, GenericGenerators.loadVar(contArg)),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restore complete. Jumping to post-invocation point"),
                GenericGenerators.jumpTo(continueExecLabelNode)
        );
    }
    
    private static InsnList restoreStateFromNormalInvocation(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        NormalInvokeContinuationPoint cp = InternalUtils.validateAndGetContinuationPoint(attrs, idx, NormalInvokeContinuationPoint.class);

        Integer lineNumber = cp.getLineNumber();
        
        VariableTable.Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        
        VariableTable.Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();

        LockVariables lockVars = attrs.getLockVariables();
        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        
        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        Type invokeReturnType = MethodInvokeUtils.getReturnTypeOfInvocation(invokeNode);
        int invokeArgCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(invokeNode);
        
        VariableTable.Variable returnCacheVar = attrs.getCacheVariables().getReturnCacheVar(invokeReturnType); // will be null if void
        
        DebugGenerators.MarkerType markerType = attrs.getSettings().getMarkerType();
        boolean debugMode = attrs.getSettings().isDebugMode();
        String dbgSig = getLogPrefix(attrs);
        
        //          enterLocks(lockState);
        //              // Load up enough of the stack to invoke the method. The invocation here needs to be wrapped in a try catch because
        //              // the original invocation was within a try catch block (at least 1, maybe more). If we do get a throwable, jump
        //              // back to the area where the original invocation was and rethrow it there so the proper catch handlers can
        //              // handle it (if the handler is for the expected throwable type).
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              continuation.addPending(methodState); // method state should be loaded from SuspendableContext.saved
        //              return <dummy>;
        //          }
        //             // At this point the invocation happened successfully, so we want to save the invocation's result, restore this
        //             // method's state, and then put the result on top of the stack as if invocation just happened. We then jump in to
        //             // the method and continue running it from the instruction after the original invocation point.
        //          tempObjVar2 = <method invocation>'s return value; // does nothing if ret type is void
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          place tempObjVar2 on top of stack if not void (as if it <method invocation> were just run and returned that value)
        //          goto restorePoint_<number>_continue;
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring INVOKE " + idx),
                // attempt to enter monitors only if method has monitorenter/exit in it (var != null if this were the case)
                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                    DebugGenerators.debugMarker(markerType, dbgSig + "Entering monitors"),
                    enterStoredMonitors(markerType, lockVars), // we MUST re-enter montiors before going further
                }),
                // Only unpack operand stack storage vars, we unpack the locals afterwards if we need to
                DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking operand stack storage variables"),
                PackStateGenerators.unpackOperandStackStorageArrays(markerType, frame, storageContainerVar, savedStackVars),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring top " + invokeArgCount + " items of operand stack (just enough to invoke)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, frame.getStackSize() - invokeArgCount, invokeArgCount),
                GenericGenerators.mergeIf(debugMode, () -> new Object[]{
                    // If in debug mode, load up the locals. This is useful if you're stepping through your coroutine in a debugger... you
                    // can look at method frames above the current one and introspect the variables (what the user expects if they're
                    // running in a debugger).
                    DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking locals storage variables (for debugMode)"),
                    PackStateGenerators.unpackLocalsStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars),
                    DebugGenerators.debugMarker(markerType, dbgSig + "Restoring locals (for debugMode)"),
                    LocalsStateGenerators.loadLocals(markerType, savedLocalsVars, frame),
                }),
                GenericGenerators.mergeIf(lineNumber != null, () -> new Object[]{
                    // We add the line number AFTER locals have been restored, so if you put in a break point at the specified line number
                    // the local vars will all show up (REMEMBER: they'll show up only if debugMode is set).
                    GenericGenerators.lineNumber(lineNumber)
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Invoking"),
                GenericGenerators.cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                GenericGenerators.ifIntegersEqual(// if we're saving after invoke, return dummy value
                        GenericGenerators.call(CONTINUATION_GETMODE_METHOD, GenericGenerators.loadVar(contArg)),
                        GenericGenerators.loadIntConst(MODE_SAVING),
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                                    DebugGenerators.debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(markerType, lockVars),
                                }),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),
                GenericGenerators.mergeIf(returnCacheVar != null, () -> new Object[] {// save return (if returnCacheVar is null means ret type is void)
                    DebugGenerators.debugMarker(markerType, dbgSig + "Saving invocation return value"),
                    GenericGenerators.saveVar(returnCacheVar)
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking locals storage variables"),
                PackStateGenerators.unpackLocalsStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring operand stack (without invoke args)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, 0, frame.getStackSize() - invokeArgCount),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring locals"),
                LocalsStateGenerators.loadLocals(markerType, savedLocalsVars, frame),
                GenericGenerators.mergeIf(returnCacheVar != null, () -> new Object[] {// load return (if returnCacheVar is null means ret type is void)
                    DebugGenerators.debugMarker(markerType, dbgSig + "Loading invocation return value"),
                    GenericGenerators.loadVar(returnCacheVar)
                }),
                // We've successfully completed our restore and we're continuing the invocation, so we need "discard" this method state
                DebugGenerators.debugMarker(markerType, dbgSig + "Discarding saved method state"),
                GenericGenerators.call(CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD, GenericGenerators.loadVar(contArg)),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restore complete. Jumping to post-invocation point"),
                GenericGenerators.jumpTo(continueExecLabelNode)
        );
    }
    
    private static InsnList restoreStateFromInvocationWithinTryCatch(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        TryCatchInvokeContinuationPoint cp = InternalUtils.validateAndGetContinuationPoint(attrs, idx, TryCatchInvokeContinuationPoint.class);

        Integer lineNumber = cp.getLineNumber();
        
        VariableTable.Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        VariableTable.Variable methodStateVar = attrs.getCoreVariables().getMethodStateVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        
        VariableTable.Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();

        LockVariables lockVars = attrs.getLockVariables();
        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        
        VariableTable.Variable throwableVar = attrs.getCacheVariables().getThrowableCacheVar();
        
        Type returnType = attrs.getSignature().getReturnType();
        
        // tryCatchBlock() invocation further on in this method will populate TryCatchBlockNode fields
        TryCatchBlockNode newTryCatchBlockNode = cp.getTryCatchBlock();

        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        LabelNode exceptionExecutionLabelNode = cp.getExceptionExecutionLabel();
        
        Type invokeReturnType = MethodInvokeUtils.getReturnTypeOfInvocation(invokeNode);
        int invokeArgCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(invokeNode);
        
        VariableTable.Variable returnCacheVar = attrs.getCacheVariables().getReturnCacheVar(invokeReturnType); // will be null if void
        
        DebugGenerators.MarkerType markerType = attrs.getSettings().getMarkerType();
        boolean debugMode = attrs.getSettings().isDebugMode();
        String dbgSig = getLogPrefix(attrs);
        
        //          enterLocks(lockState);
        //          continuation.addPending(methodState); // method state should be loaded from SuspendableContext.saved
        //              // Load up enough of the stack to invoke the method. The invocation here needs to be wrapped in a try catch because
        //              // the original invocation was within a try catch block (at least 1, maybe more). If we do get a throwable, jump
        //              // back to the area where the original invocation was and rethrow it there so the proper catch handlers can
        //              // handle it (if the handler is for the expected throwable type).
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          try {
        //              <method invocation>
        //          } catch (throwable) {
        //              tempObjVar2 = throwable;
        //              restoreOperandStack(stack);
        //              restoreLocalsStack(localVars);
        //              goto restorePoint_<number>_rethrow;
        //          }
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //             // At this point the invocation happened successfully, so we want to save the invocation's result, restore this
        //             // method's state, and then put the result on top of the stack as if invocation just happened. We then jump in to
        //             // the method and continue running it from the instruction after the original invocation point.
        //          tempObjVar2 = <method invocation>'s return value; // does nothing if ret type is void
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          place tempObjVar2 on top of stack if not void (as if it <method invocation> were just run and returned that value)
        //          goto restorePoint_<number>_continue;
        
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring INVOKE WITHIN TRYCATCH " + idx),
                // attempt to enter monitors only if method has monitorenter/exit in it (var != null if this were the case)
                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                    DebugGenerators.debugMarker(markerType, dbgSig + "Entering monitors"),
                    enterStoredMonitors(markerType, lockVars), // we MUST re-enter montiors before going further
                }),
                // Only unpack operand stack storage vars, we unpack the locals afterwards if we need to
                DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking operand stack storage variables"),
                PackStateGenerators.unpackOperandStackStorageArrays(markerType, frame, storageContainerVar, savedStackVars),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring top " + invokeArgCount + " items of operand stack (just enough to invoke)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, frame.getStackSize() - invokeArgCount, invokeArgCount),
                GenericGenerators.mergeIf(debugMode, () -> new Object[]{
                    // If in debug mode, load up the locals. This is useful if you're stepping through your coroutine in a debugger... you
                    // can look at method frames above the current one and introspect the variables (what the user expects if they're
                    // running in a debugger).
                    DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking locals storage variables (for debugMode)"),
                    PackStateGenerators.unpackLocalsStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars),
                    DebugGenerators.debugMarker(markerType, dbgSig + "Restoring locals (for debugMode)"),
                    LocalsStateGenerators.loadLocals(markerType, savedLocalsVars, frame),
                }),
                GenericGenerators.mergeIf(lineNumber != null, () -> new Object[]{
                    // We add the line number AFTER locals have been restored, so if you put in a break point at the specified line number
                    // the local vars will all show up (REMEMBER: they'll show up only if debugMode is set).
                    GenericGenerators.lineNumber(lineNumber)
                }),
                GenericGenerators.tryCatchBlock(newTryCatchBlockNode,
                        null,
                        GenericGenerators.merge(// try
                                DebugGenerators.debugMarker(markerType, dbgSig + "Invoking (within custom try-catch)"),
                                GenericGenerators.cloneInvokeNode(invokeNode) // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                        ),
                        GenericGenerators.merge(// catch(any)
                                DebugGenerators.debugMarker(markerType, dbgSig + "Throwable caught"),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Saving caught throwable"),
                                GenericGenerators.saveVar(throwableVar),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking locals storage variables"),
                                PackStateGenerators.unpackLocalsStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring operand stack (without invoke args)"),
                                loadOperandStack(markerType, savedStackVars, frame, 0, 0, frame.getStackSize() - invokeArgCount),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring locals"),
                                LocalsStateGenerators.loadLocals(markerType, savedLocalsVars, frame),
                                // We caught an exception, which means that everything that was invoked after us is pretty much gone and
                                // we're continuing the invocation as if we restore, we need to "discard" this method state along with
                                // everything after it.
                                DebugGenerators.debugMarker(markerType, dbgSig + "Discarding saved method states up until this point (unwinding)"),
                                GenericGenerators.call(CONTINUATION_UNLOADMETHODSTATETOBEFORE_METHOD, GenericGenerators.loadVar(contArg), GenericGenerators.loadVar(methodStateVar)),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Restore complete. Jumping to rethrow point (within orig trycatch block)"),
                                GenericGenerators.jumpTo(exceptionExecutionLabelNode)
                        )
                ),
                GenericGenerators.ifIntegersEqual(// if we're saving after invoke, return dummy value
                        GenericGenerators.call(CONTINUATION_GETMODE_METHOD, GenericGenerators.loadVar(contArg)),
                        GenericGenerators.loadIntConst(MODE_SAVING),
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                                    DebugGenerators.debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(markerType, lockVars),
                                }),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),
                GenericGenerators.mergeIf(returnCacheVar != null, () -> new Object[] {// save return (if returnCacheVar is null means ret type is void)
                    DebugGenerators.debugMarker(markerType, dbgSig + "Saving invocation return value"),
                    GenericGenerators.saveVar(returnCacheVar)
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Unpacking locals storage variables"),
                PackStateGenerators.unpackLocalsStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring operand stack (without invoke args)"),
                loadOperandStack(markerType, savedStackVars, frame, 0, 0, frame.getStackSize() - invokeArgCount),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restoring locals"),
                LocalsStateGenerators.loadLocals(markerType, savedLocalsVars, frame),
                GenericGenerators.mergeIf(returnCacheVar != null, () -> new Object[] {// load return (if returnCacheVar is null means ret type is void)
                    DebugGenerators.debugMarker(markerType, dbgSig + "Loading invocation return value"),
                    GenericGenerators.loadVar(returnCacheVar)
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Discarding saved method state"),
                GenericGenerators.call(CONTINUATION_UNLOADCURRENTMETHODSTATE_METHOD, GenericGenerators.loadVar(contArg)),
                DebugGenerators.debugMarker(markerType, dbgSig + "Restore complete. Jumping to post-invocation point"),
                GenericGenerators.jumpTo(continueExecLabelNode)
        );
    }

    
    
    
    
    
    

    

    
    
    
    
    public static InsnList saveState(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        ContinuationPoint continuationPoint = InternalUtils.validateAndGetContinuationPoint(attrs, idx, ContinuationPoint.class);
        
        
                
        InsnList saveInsnList;
        if (continuationPoint instanceof SuspendContinuationPoint) {
            saveInsnList = saveStateFromSuspend(attrs, idx);
        } else if (continuationPoint instanceof NormalInvokeContinuationPoint) {
            saveInsnList = saveStateFromNormalInvocation(attrs, idx);
        } else if (continuationPoint instanceof TryCatchInvokeContinuationPoint) {
            saveInsnList = saveStateFromInvocationWithinTryCatch(attrs, idx);
        } else {
            throw new IllegalArgumentException(); // should never happen
        }
        
        return saveInsnList;
    }
    
    private static InsnList saveStateFromSuspend(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        SuspendContinuationPoint cp = InternalUtils.validateAndGetContinuationPoint(attrs, idx, SuspendContinuationPoint.class);

        String friendlyClassName = attrs.getSignature().getClassName().replace('/', '.'); // '/' -> '.'   because it's non-internal format
        int methodId = attrs.getSignature().getMethodId();
        
        Integer lineNumber = cp.getLineNumber();

        VariableTable.Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        VariableTable.Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        LockVariables lockVars = attrs.getLockVariables();
        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        
        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        DebugGenerators.MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocals();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          continuation.setMode(MODE_SAVING);
        //          exitLocks(lockState);
        //          return <dummy>;
        //
        //
        //          restorePoint_<number>_continue: // at this label: empty exec stack / uninit exec var table
        return GenericGenerators.merge(
                GenericGenerators.mergeIf(lineNumber != null, () -> new Object[]{
                    GenericGenerators.lineNumber(lineNumber)
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Saving SUSPEND " + idx),
                DebugGenerators.debugMarker(markerType, dbgSig + "Saving operand stack"),
                saveOperandStack(markerType, savedStackVars, frame), // REMEMBER: STACK IS TOTALLY EMPTY AFTER THIS. ALSO, DON'T FORGET THAT
                                                                     // SuspendableContext OBJECT WILL BE TOP ITEM, NEEDS TO BE DISCARDED ON LOAD
                DebugGenerators.debugMarker(markerType, dbgSig + "Saving locals"),
                LocalsStateGenerators.saveLocals(markerType, savedLocalsVars, frame),
                DebugGenerators.debugMarker(markerType, dbgSig + "Packing locals and operand stack in to container"),
                PackStateGenerators.packStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                DebugGenerators.debugMarker(markerType, dbgSig + "Creating and pushing method state"),
                GenericGenerators.call(CONTINUATION_PUSHNEWMETHODSTATE_METHOD, GenericGenerators.loadVar(contArg),
                        GenericGenerators.construct(METHODSTATE_INIT_METHOD,
                                GenericGenerators.loadStringConst(friendlyClassName),
                                GenericGenerators.loadIntConst(methodId),
                                GenericGenerators.loadIntConst(idx),
                                GenericGenerators.loadVar(storageContainerVar),
                                // load lockstate for last arg if method actually has monitorenter/exit in it
                                // (var != null if this were the case), otherwise load null for that arg
                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[] {
                                    GenericGenerators.loadVar(lockStateVar)
                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                    GenericGenerators.loadNull()
                                }).generate()
                        )
                ),
                DebugGenerators.debugMarker(markerType, dbgSig + "Setting mode to save"),
                GenericGenerators.call(CONTINUATION_SETMODE_METHOD, GenericGenerators.loadVar(contArg), GenericGenerators.loadIntConst(MODE_SAVING)),
                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                    DebugGenerators.debugMarker(markerType, dbgSig + "Exiting monitors"),
                    exitStoredMonitors(markerType, lockVars),
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                returnDummy(returnType), // return dummy value
                
                
                
                GenericGenerators.addLabel(continueExecLabelNode),
                DebugGenerators.debugMarker(markerType, dbgSig + "Continuing execution...")
        );
    }
    
    private static InsnList saveStateFromNormalInvocation(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        NormalInvokeContinuationPoint cp = InternalUtils.validateAndGetContinuationPoint(attrs, idx, NormalInvokeContinuationPoint.class);

        String friendlyClassName = attrs.getSignature().getClassName().replace('/', '.'); // '/' -> '.'   because it's non-internal format
        int methodId = attrs.getSignature().getMethodId();

        Integer lineNumber = cp.getLineNumber();

        VariableTable.Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        VariableTable.Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        LockVariables lockVars = attrs.getLockVariables();
        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();

        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        
        DebugGenerators.MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);
        
        //          Object[] duplicatedArgs = saveOperandStack(<method param count>); -- Why do we do this? because when we want to save the
        //                                                                            -- args to this method when we call
        //                                                                            -- saveOperandStack(). We need to save here becuase
        //                                                                            -- once we invoke the method the args will be consumed
        //                                                                            -- off the stack. The args need to be saved because
        //                                                                            -- when we load, we need to call in to this method
        //                                                                            -- again (see loading code generator above).
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              Object[] stack = saveOperandStack();
        //              Object[] locals = saveLocals();
        //              exitLocks(lockState);
        //              continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //              return <dummy>;
        //          }
        //
        //
        //          restorePoint_<number>_continue:
        
        int invokeArgCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(invokeNode);
        return GenericGenerators.merge(
                GenericGenerators.mergeIf(lineNumber != null, () -> new Object[]{
                    GenericGenerators.lineNumber(lineNumber)
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Saving INVOKE " + idx),
                DebugGenerators.debugMarker(markerType, dbgSig + "Saving top " + invokeArgCount + " items of operand stack (args for invoke)"),
                saveOperandStack(markerType, savedStackVars, frame, invokeArgCount),
                DebugGenerators.debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack (for invoke)"),
                loadOperandStack(markerType, savedStackVars, frame,
                        frame.getStackSize() - invokeArgCount,
                        frame.getStackSize() - invokeArgCount,
                        invokeArgCount),
                DebugGenerators.debugMarker(markerType, dbgSig + "Invoking"),
                GenericGenerators.cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                GenericGenerators.ifIntegersEqual(// if we're saving after invoke
                        GenericGenerators.call(CONTINUATION_GETMODE_METHOD, GenericGenerators.loadVar(contArg)),
                        GenericGenerators.loadIntConst(MODE_SAVING),
                        GenericGenerators.merge(
                                DebugGenerators.debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack (for full save)"),
                                loadOperandStack(markerType, savedStackVars, frame,
                                        frame.getStackSize() - invokeArgCount,
                                        frame.getStackSize() - invokeArgCount,
                                        invokeArgCount),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Saving operand stack"),
                                saveOperandStack(markerType, savedStackVars, frame), // REMEMBER: STACK IS TOTALLY EMPTY AFTER THIS
                                DebugGenerators.debugMarker(markerType, dbgSig + "Saving locals"),
                                LocalsStateGenerators.saveLocals(markerType, savedLocalsVars, frame),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Packing locals and operand stack in to container"),
                                PackStateGenerators.packStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                                    DebugGenerators.debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(markerType, lockVars),
                                }),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Creating and pushing method state"),
                                GenericGenerators.call(CONTINUATION_PUSHNEWMETHODSTATE_METHOD, GenericGenerators.loadVar(contArg),
                                        GenericGenerators.construct(METHODSTATE_INIT_METHOD,
                                                GenericGenerators.loadStringConst(friendlyClassName),
                                                GenericGenerators.loadIntConst(methodId),
                                                GenericGenerators.loadIntConst(idx),
                                                GenericGenerators.loadVar(storageContainerVar),
                                                // load lockstate for last arg if method actually has monitorenter/exit in it
                                                // (var != null if this were the case), otherwise load null for that arg
                                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[] {
                                                    GenericGenerators.loadVar(lockStateVar)
                                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                                    GenericGenerators.loadNull()
                                                }).generate()
                                        )
                                ),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),      

                
                
                
                GenericGenerators.addLabel(continueExecLabelNode),
                DebugGenerators.debugMarker(markerType, dbgSig + "Continuing execution...")
        );
    }
    
    private static InsnList saveStateFromInvocationWithinTryCatch(MethodAttributes attrs, int idx) {
        Validate.notNull(attrs);
        Validate.isTrue(idx >= 0);
        TryCatchInvokeContinuationPoint cp = InternalUtils.validateAndGetContinuationPoint(attrs, idx, TryCatchInvokeContinuationPoint.class);

        String friendlyClassName = attrs.getSignature().getClassName().replace('/', '.'); // '/' -> '.'   because it's non-internal format
        int methodId = attrs.getSignature().getMethodId();

        Integer lineNumber = cp.getLineNumber();

        VariableTable.Variable contArg = attrs.getCoreVariables().getContinuationArgVar();
        StorageVariables savedLocalsVars = attrs.getLocalsStorageVariables();
        StorageVariables savedStackVars = attrs.getStackStorageVariables();
        VariableTable.Variable storageContainerVar = attrs.getStorageContainerVariables().getContainerVar();
        
        LockVariables lockVars = attrs.getLockVariables();
        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();

        VariableTable.Variable throwableVar = attrs.getCacheVariables().getThrowableCacheVar();

        Type returnType = attrs.getSignature().getReturnType();
        
        Frame<BasicValue> frame = cp.getFrame();
        MethodInsnNode invokeNode = cp.getInvokeInstruction();
        LabelNode continueExecLabelNode = cp.getContinueExecutionLabel();
        LabelNode exceptionExecutionLabelNode = cp.getExceptionExecutionLabel();
        
        DebugGenerators.MarkerType markerType = attrs.getSettings().getMarkerType();
        String dbgSig = getLogPrefix(attrs);

        int invokeArgCount = MethodInvokeUtils.getArgumentCountRequiredForInvocation(invokeNode);
        return GenericGenerators.merge(
                GenericGenerators.mergeIf(lineNumber != null, () -> new Object[]{
                    GenericGenerators.lineNumber(lineNumber)
                }),
                DebugGenerators.debugMarker(markerType, dbgSig + "Saving INVOKE WITHIN TRYCATCH " + idx),
                DebugGenerators.debugMarker(markerType, dbgSig + "Saving top " + invokeArgCount + " items of operand stack (args for invoke)"),
                saveOperandStack(markerType, savedStackVars, frame, invokeArgCount),
                DebugGenerators.debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack (for invoke)"),
                loadOperandStack(markerType, savedStackVars, frame,
                        frame.getStackSize() - invokeArgCount,
                        frame.getStackSize() - invokeArgCount,
                        invokeArgCount),
                DebugGenerators.debugMarker(markerType, dbgSig + "Invoking"),
                GenericGenerators.cloneInvokeNode(invokeNode), // invoke method  (ADDED MULTIPLE TIMES -- MUST BE CLONED)
                GenericGenerators.ifIntegersEqual(// if we're saving after invoke, return dummy value
                        GenericGenerators.call(CONTINUATION_GETMODE_METHOD, GenericGenerators.loadVar(contArg)),
                        GenericGenerators.loadIntConst(MODE_SAVING),
                        GenericGenerators.merge(DebugGenerators.debugMarker(markerType, dbgSig + "Mode set to save on return"),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Popping dummy return value off stack"),
                                popMethodResult(invokeNode),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Reloading invoke arguments back on to the stack"),
                                loadOperandStack(markerType, savedStackVars, frame,
                                        frame.getStackSize() - invokeArgCount,
                                        frame.getStackSize() - invokeArgCount,
                                        invokeArgCount),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Saving operand stack"),
                                saveOperandStack(markerType, savedStackVars, frame), // REMEMBER: STACK IS TOTALLY EMPTY AFTER THIS
                                DebugGenerators.debugMarker(markerType, dbgSig + "Saving locals"),
                                LocalsStateGenerators.saveLocals(markerType, savedLocalsVars, frame),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Packing locals and operand stack in to container"),
                                PackStateGenerators.packStorageArrays(markerType, frame, storageContainerVar, savedLocalsVars, savedStackVars),
                                // attempt to exit monitors only if method has monitorenter/exit in it (var != null if this were the case)
                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[]{
                                    DebugGenerators.debugMarker(markerType, dbgSig + "Exiting monitors"),
                                    exitStoredMonitors(markerType, lockVars),
                                }),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Creating and pushing method state"),
                                GenericGenerators.call(CONTINUATION_PUSHNEWMETHODSTATE_METHOD, GenericGenerators.loadVar(contArg),
                                        GenericGenerators.construct(METHODSTATE_INIT_METHOD,
                                                GenericGenerators.loadStringConst(friendlyClassName),
                                                GenericGenerators.loadIntConst(methodId),
                                                GenericGenerators.loadIntConst(idx),
                                                GenericGenerators.loadVar(storageContainerVar),
                                                // load lockstate for last arg if method actually has monitorenter/exit in it
                                                // (var != null if this were the case), otherwise load null for that arg
                                                GenericGenerators.mergeIf(lockStateVar != null, () -> new Object[] {
                                                    GenericGenerators.loadVar(lockStateVar)
                                                }).mergeIf(lockStateVar == null, () -> new Object[] {
                                                    GenericGenerators.loadNull()
                                                }).generate()
                                        )
                                ),
                                DebugGenerators.debugMarker(markerType, dbgSig + "Returning (dummy return value if not void)"),
                                returnDummy(returnType)
                        )
                ),
                DebugGenerators.debugMarker(markerType, dbgSig + "Jumping to continue execution point..."),
                GenericGenerators.jumpTo(continueExecLabelNode),
                
                
                
                GenericGenerators.addLabel(exceptionExecutionLabelNode),
                // Since we're rethrowing from original try/catch, if the throwable is of the expected type it'll get handled. If not it'll
                // get thrown up the chain (which is normal behaviour).
                DebugGenerators.debugMarker(markerType, dbgSig + "Rethrowing throwable from original try/catch"),
                GenericGenerators.loadVar(throwableVar),
                GenericGenerators.throwThrowable(),
                
                
                
                GenericGenerators.addLabel(continueExecLabelNode),
                DebugGenerators.debugMarker(markerType, dbgSig + "Continuing execution...")
        );
    }
    
    
    
    
    
    
    
    
    
    /**
     * Generates instructions that returns a dummy value. Return values are as follows:
     * <ul>
     * <li>void -&gt; no value</li>
     * <li>boolean -&gt; false</li>
     * <li>byte/short/char/int -&gt; 0</li>
     * <li>long -&gt; 0L</li>
     * <li>float -&gt; 0.0f</li>
     * <li>double -&gt; 0.0</li>
     * <li>Object -&gt; null</li>
     * </ul>
     *
     * @param returnType return type of the method this generated bytecode is for
     * @return instructions to return a dummy value
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code returnType}'s sort is of {@link Type#METHOD}
     */
    private static InsnList returnDummy(Type returnType) {
        Validate.notNull(returnType);
        Validate.isTrue(returnType.getSort() != Type.METHOD);

        InsnList ret = new InsnList();

        switch (returnType.getSort()) {
            case Type.VOID:
                ret.add(new InsnNode(Opcodes.RETURN));
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                ret.add(new InsnNode(Opcodes.ICONST_0));
                ret.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.LONG:
                ret.add(new InsnNode(Opcodes.LCONST_0));
                ret.add(new InsnNode(Opcodes.LRETURN));
                break;
            case Type.FLOAT:
                ret.add(new InsnNode(Opcodes.FCONST_0));
                ret.add(new InsnNode(Opcodes.FRETURN));
                break;
            case Type.DOUBLE:
                ret.add(new InsnNode(Opcodes.DCONST_0));
                ret.add(new InsnNode(Opcodes.DRETURN));
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                ret.add(new InsnNode(Opcodes.ACONST_NULL));
                ret.add(new InsnNode(Opcodes.ARETURN));
                break;
            default:
                throw new IllegalStateException();
        }

        return ret;
    }
    
    /**
     * Generates instructions to pop the result of the method off the stack. This will only generate instructions if the method being
     * invoked generates a return value.
     * @param invokeInsnNode instruction for the method that was invoked (can either be of type {@link MethodInsnNode} or
     * {@link InvokeDynamicInsnNode} -- this is used to determine how many items to pop off the stack
     * @return instructions for a pop (only if the method being invoked generates a return value)
     * @throws IllegalArgumentException if {@code invokeInsnNode} isn't of type {@link MethodInsnNode} or {@link InvokeDynamicInsnNode}
     * @throws NullPointerException if any argument is {@code null}
     */
    private static InsnList popMethodResult(AbstractInsnNode invokeInsnNode) {
        Validate.notNull(invokeInsnNode);
        
        Type returnType = MethodInvokeUtils.getReturnTypeOfInvocation(invokeInsnNode);
        
        InsnList ret = new InsnList();
        switch (returnType.getSort()) {
            case Type.LONG:
            case Type.DOUBLE:
                ret.add(new InsnNode(Opcodes.POP2));
                break;
            case Type.VOID:
                break;
            case Type.METHOD:
                throw new IllegalStateException(); // this should never happen
            default:
                ret.add(new InsnNode(Opcodes.POP));
                break;
        }

        return ret;
    }
    
    private static String getLogPrefix(MethodAttributes attrs) {
        return attrs.getSignature().getClassName() + "-"
                + attrs.getSignature().getMethodName() + "-"
                + attrs.getSignature().getMethodDescriptor() + " >>> ";
    }
}
