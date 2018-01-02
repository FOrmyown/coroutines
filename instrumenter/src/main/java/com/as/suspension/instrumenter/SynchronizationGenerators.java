/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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

import static com.as.suspension.instrumenter.generators.GenericGenerators.call;

import com.as.suspension.instrumenter.asm.VariableTable;
import com.as.suspension.instrumenter.generators.DebugGenerators;
import com.as.suspension.instrumenter.generators.GenericGenerators;
import com.as.suspension.user.LockState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class SynchronizationGenerators {

    private static final Constructor<LockState> LOCKSTATE_INIT_METHOD
            = ConstructorUtils.getAccessibleConstructor(LockState.class);
    private static final Method LOCKSTATE_ENTER_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "enter", Object.class);
    private static final Method LOCKSTATE_EXIT_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "exit", Object.class);
    private static final Method LOCKSTATE_TOARRAY_METHOD
            = MethodUtils.getAccessibleMethod(LockState.class, "toArray");

    private SynchronizationGenerators() {
        // do nothing
    }
    
    /**
     * Generates instruction to that creates a new {@link LockState} object and saves it to the lockstate variable.
     * @param markerType debug marker type
     * @param lockVars variables for lock/synchpoint functionality
     * @return instructions to push a new {@link LockState} object
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if lock variables aren't set (the method doesn't contain any monitorenter/monitorexit instructions)
     */
    public static InsnList createMonitorContainer(DebugGenerators.MarkerType markerType, LockVariables lockVars) {
        Validate.notNull(markerType);
        Validate.notNull(lockVars);

        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        Validate.isTrue(lockStateVar != null);  // extra sanity check, if no synch points this should be null

        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, "Creating lockstate"),
                GenericGenerators.construct(LOCKSTATE_INIT_METHOD),
                GenericGenerators.saveVar(lockStateVar)
        );
    }

    /**
     * Generates instruction to enter all monitors in the {@link LockState} object sitting in the lockstate variable.
     * @param markerType debug marker type
     * @param lockVars variables for lock/synchpoint functionality
     * @return instructions to enter all monitors in the {@link LockState} object
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if lock variables aren't set (the method doesn't contain any monitorenter/monitorexit instructions)
     */
    public static InsnList enterStoredMonitors(DebugGenerators.MarkerType markerType, LockVariables lockVars) {
        Validate.notNull(markerType);
        Validate.notNull(lockVars);

        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        VariableTable.Variable counterVar = lockVars.getCounterVar();
        VariableTable.Variable arrayLenVar = lockVars.getArrayLenVar();
        Validate.isTrue(lockStateVar != null);
        Validate.isTrue(counterVar != null);
        Validate.isTrue(arrayLenVar != null);

        return GenericGenerators.forEach(counterVar, arrayLenVar,
                GenericGenerators.merge(
                        DebugGenerators.debugMarker(markerType, "Loading monitors to enter"),
                        GenericGenerators.call(LOCKSTATE_TOARRAY_METHOD, GenericGenerators.loadVar(lockStateVar))
                ),
                GenericGenerators.merge(
                        DebugGenerators.debugMarker(markerType, "Entering monitor"),
                        new InsnNode(Opcodes.MONITORENTER)
                )
        );
    }
    
    /**
     * Generates instruction to exit all monitors in the {@link LockState} object sitting in the lockstate variable.
     * @param markerType debug marker type
     * @param lockVars variables for lock/synchpoint functionality
     * @return instructions to exit all monitors in the {@link LockState} object
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if lock variables aren't set (the method doesn't contain any monitorenter/monitorexit instructions)
     */
    public static InsnList exitStoredMonitors(DebugGenerators.MarkerType markerType, LockVariables lockVars) {
        Validate.notNull(markerType);
        Validate.notNull(lockVars);

        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        VariableTable.Variable counterVar = lockVars.getCounterVar();
        VariableTable.Variable arrayLenVar = lockVars.getArrayLenVar();
        Validate.isTrue(lockStateVar != null);
        Validate.isTrue(counterVar != null);
        Validate.isTrue(arrayLenVar != null);

        return GenericGenerators.forEach(counterVar, arrayLenVar,
                GenericGenerators.merge(
                        DebugGenerators.debugMarker(markerType, "Loading monitors to exit"),
                        GenericGenerators.call(LOCKSTATE_TOARRAY_METHOD, GenericGenerators.loadVar(lockStateVar))
                ),
                GenericGenerators.merge(
                        DebugGenerators.debugMarker(markerType, "Exitting monitor"),
                        new InsnNode(Opcodes.MONITOREXIT)
                )
        );
    }

    /**
     * Generates instruction to enter a monitor (top item on the stack) and store it in the {@link LockState} object sitting in the
     * lockstate variable.
     * @param markerType debug marker type
     * @param lockVars variables for lock/synchpoint functionality
     * @return instructions to enter a monitor and store it in the {@link LockState} object
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if lock variables aren't set (the method doesn't contain any monitorenter/monitorexit instructions)
     */
    public static InsnList enterMonitorAndStore(DebugGenerators.MarkerType markerType, LockVariables lockVars) {
        Validate.notNull(markerType);
        Validate.notNull(lockVars);

        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        Validate.isTrue(lockStateVar != null);

        Type clsType = Type.getType(LOCKSTATE_ENTER_METHOD.getDeclaringClass());
        Type methodType = Type.getType(LOCKSTATE_ENTER_METHOD);
        String clsInternalName = clsType.getInternalName();
        String methodDesc = methodType.getDescriptor();
        String methodName = LOCKSTATE_ENTER_METHOD.getName();

        // NOTE: This adds to the lock state AFTER locking.
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, "Entering monitor and storing"),
                                                                         // [obj]
                new InsnNode(Opcodes.DUP),                               // [obj, obj]
                new InsnNode(Opcodes.MONITORENTER),                      // [obj]
                new VarInsnNode(Opcodes.ALOAD, lockStateVar.getIndex()), // [obj, lockState]
                new InsnNode(Opcodes.SWAP),                              // [lockState, obj]
                new MethodInsnNode(Opcodes.INVOKEVIRTUAL,                // []
                        clsInternalName,
                        methodName,
                        methodDesc,
                        false)
        );
    }

    /**
     * Generates instruction to exit a monitor (top item on the stack) and remove it from the {@link LockState} object sitting in the
     * lockstate variable.
     * @param markerType debug marker type
     * @param lockVars variables for lock/synchpoint functionality
     * @return instructions to exit a monitor and remove it from the {@link LockState} object
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if lock variables aren't set (the method doesn't contain any monitorenter/monitorexit instructions)
     */
    public static InsnList exitMonitorAndDelete(DebugGenerators.MarkerType markerType, LockVariables lockVars) {
        Validate.notNull(markerType);
        Validate.notNull(lockVars);

        VariableTable.Variable lockStateVar = lockVars.getLockStateVar();
        Validate.isTrue(lockStateVar != null);

        Type clsType = Type.getType(LOCKSTATE_EXIT_METHOD.getDeclaringClass());
        Type methodType = Type.getType(LOCKSTATE_EXIT_METHOD);
        String clsInternalName = clsType.getInternalName();
        String methodDesc = methodType.getDescriptor();
        String methodName = LOCKSTATE_EXIT_METHOD.getName();
        
        // NOTE: This removes the lock AFTER unlocking.
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, "Exiting monitor and unstoring"),
                                                                         // [obj]
                new InsnNode(Opcodes.DUP),                               // [obj, obj]
                new InsnNode(Opcodes.MONITOREXIT),                       // [obj]
                new VarInsnNode(Opcodes.ALOAD, lockStateVar.getIndex()), // [obj, lockState]
                new InsnNode(Opcodes.SWAP),                              // [lockState, obj]
                new MethodInsnNode(Opcodes.INVOKEVIRTUAL,                // []
                        clsInternalName,
                        methodName,
                        methodDesc,
                        false)
        );
    }
}
