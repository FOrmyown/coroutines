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

import com.as.suspension.instrumenter.asm.VariableTable;
import com.as.suspension.instrumenter.generators.DebugGenerators;
import com.as.suspension.instrumenter.generators.GenericGenerators;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Utility class to generate bytecode instructions that pack/unpack storage arrays for the operand stack and local variables table into an
 * Object array.
 * @author Kasra Faghihi
 */
final class PackStateGenerators {
    private PackStateGenerators() {
        // do nothing
    }
    
    // Why are we packing/unpacking in to a single Object[]? It turns out that if we compare ...
    //
    // 1. Assigning each storage array to a field in MethodState.
    //   ... vs ...
    // 2. Creating a new Object[] and shoving all the storage arrays in there, then assigning that Object[] to a single field in
    //    MethodState.
    //
    // Item number 2 seems noticably faster. I'm not exactly sure why this is, and it may not apply to every platform, but we're going to
    // exploit this micro-optimization for the time being.
    //
    // See ObjectArrayVsHolderBenchmark class to see the benchmark used to determine this.
    
    public static InsnList packStorageArrays(DebugGenerators.MarkerType markerType, Frame<BasicValue> frame, VariableTable.Variable containerVar,
                                             StorageVariables localsStorageVars, StorageVariables operandStackStorageVars) {
        Validate.notNull(markerType);
        Validate.notNull(frame);
        Validate.notNull(containerVar);
        Validate.notNull(localsStorageVars);
        Validate.notNull(operandStackStorageVars);
        
        VariableTable.Variable localsIntsVar = localsStorageVars.getIntStorageVar();
        VariableTable.Variable localsFloatsVar = localsStorageVars.getFloatStorageVar();
        VariableTable.Variable localsLongsVar = localsStorageVars.getLongStorageVar();
        VariableTable.Variable localsDoublesVar = localsStorageVars.getDoubleStorageVar();
        VariableTable.Variable localsObjectsVar = localsStorageVars.getObjectStorageVar();
        VariableTable.Variable stackIntsVar = operandStackStorageVars.getIntStorageVar();
        VariableTable.Variable stackFloatsVar = operandStackStorageVars.getFloatStorageVar();
        VariableTable.Variable stackLongsVar = operandStackStorageVars.getLongStorageVar();
        VariableTable.Variable stackDoublesVar = operandStackStorageVars.getDoubleStorageVar();
        VariableTable.Variable stackObjectsVar = operandStackStorageVars.getObjectStorageVar();
        
        StorageSizes stackSizes = OperandStackStateGenerators.computeSizes(frame, 0, frame.getStackSize());
        StorageSizes localsSizes = LocalsStateGenerators.computeSizes(frame);
        
        // Why are we using size > 0 vs checking to see if var != null?
        //
        // REMEMBER THAT the analyzer will determine the variable slots to create for storage array based on its scan of EVERY
        // continuation/suspend point in the method. Imagine the method that we're instrumenting is this...
        //
        // public void example(SuspendableContext c, String arg1) {
        //     String var1 = "hi";
        //     c.suspend();     
        //
        //     System.out.println(var1);
        //     int var2 = 5;
        //     c.suspend();
        //
        //     System.out.println(var1 + var2);
        // }
        //
        // There are two continuation/suspend points. The analyzer determines that method will need to assign variable slots for
        // localsObjectsVar+localsIntsVar. All the other locals vars will be null.
        //
        // If we ended up using var != null instead of size > 0, things would mess up on the first suspend(). The only variable initialized
        // at the first suspend is var1. As such, LocalStateGenerator ONLY CREATES AN ARRAY FOR localsObjectsVar. It doesn't touch
        // localsIntsVar because, at the first suspend(), var2 is UNINITALIZED. Nothing has been set to that variable slot.
        //
        //
        // The same thing applies to the operand stack. It doesn't make sense to create arrays for operand stack types that don't exist yet
        // at a continuation point, even though they may exist at other continuation points furhter down
                
        
        // Storage arrays in to locals container
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, "Packing storage arrays for locals and operand stack in to an Object[]"),
                new LdcInsnNode(10),
                new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"),
                new VarInsnNode(Opcodes.ASTORE, containerVar.getIndex()),
                GenericGenerators.mergeIf(localsSizes.getIntsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting locals ints in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(0),                                            // [Object[], 0]
                    new VarInsnNode(Opcodes.ALOAD, localsIntsVar.getIndex()),      // [Object[], 0, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(localsSizes.getFloatsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting locals floats in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(1),                                            // [Object[], 1]
                    new VarInsnNode(Opcodes.ALOAD, localsFloatsVar.getIndex()),    // [Object[], 1, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(localsSizes.getLongsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting locals longs in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(2),                                            // [Object[], 2]
                    new VarInsnNode(Opcodes.ALOAD, localsLongsVar.getIndex()),     // [Object[], 2, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(localsSizes.getDoublesSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting locals doubles in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(3),                                            // [Object[], 3]
                    new VarInsnNode(Opcodes.ALOAD, localsDoublesVar.getIndex()),   // [Object[], 3, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(localsSizes.getObjectsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting locals objects in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(4),                                            // [Object[], 4]
                    new VarInsnNode(Opcodes.ALOAD, localsObjectsVar.getIndex()),   // [Object[], 4, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(stackSizes.getIntsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting stack ints in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(5),                                            // [Object[], 5]
                    new VarInsnNode(Opcodes.ALOAD, stackIntsVar.getIndex()),       // [Object[], 5, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(stackSizes.getFloatsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting stack floats in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(6),                                            // [Object[], 6]
                    new VarInsnNode(Opcodes.ALOAD, stackFloatsVar.getIndex()),     // [Object[], 6, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(stackSizes.getLongsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting stack longs in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(7),                                            // [Object[], 7]
                    new VarInsnNode(Opcodes.ALOAD, stackLongsVar.getIndex()),      // [Object[], 7, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(stackSizes.getDoublesSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting stack doubles in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(8),                                            // [Object[], 8]
                    new VarInsnNode(Opcodes.ALOAD, stackDoublesVar.getIndex()),    // [Object[], 8, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                }),
                GenericGenerators.mergeIf(stackSizes.getObjectsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Putting stack objects in to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),       // [Object[]]
                    new LdcInsnNode(9),                                            // [Object[], 9]
                    new VarInsnNode(Opcodes.ALOAD, stackObjectsVar.getIndex()),    // [Object[], 9, val]
                    new InsnNode(Opcodes.AASTORE),                                 // []
                })
        );
    }
    
    public static InsnList unpackLocalsStorageArrays(DebugGenerators.MarkerType markerType, Frame<BasicValue> frame, VariableTable.Variable containerVar,
                                                     StorageVariables localsStorageVars) {
        Validate.notNull(markerType);
        Validate.notNull(containerVar);
        Validate.notNull(localsStorageVars);
        
        VariableTable.Variable localsIntsVar = localsStorageVars.getIntStorageVar();
        VariableTable.Variable localsFloatsVar = localsStorageVars.getFloatStorageVar();
        VariableTable.Variable localsLongsVar = localsStorageVars.getLongStorageVar();
        VariableTable.Variable localsDoublesVar = localsStorageVars.getDoubleStorageVar();
        VariableTable.Variable localsObjectsVar = localsStorageVars.getObjectStorageVar();
        
        StorageSizes localsSizes = LocalsStateGenerators.computeSizes(frame);
        
        // Why are we using size > 0 vs checking to see if var != null?
        //
        // REMEMBER THAT the analyzer will determine the variable slots to create for storage array based on its scan of EVERY
        // continuation/suspend point in the method. Imagine the method that we're instrumenting is this...
        //
        // public void example(SuspendableContext c, String arg1) {
        //     String var1 = "hi";
        //     c.suspend();     
        //
        //     System.out.println(var1);
        //     int var2 = 5;
        //     c.suspend();
        //
        //     System.out.println(var1 + var2);
        // }
        //
        // There are two continuation/suspend points. The analyzer determines that method will need to assign variable slots for
        // localsObjectsVar+localsIntsVar. All the other locals vars will be null.
        //
        // If we ended up using var != null instead of size > 0, things would mess up on the first suspend(). The only variable initialized
        // at the first suspend is var1. As such, LocalStateGenerator ONLY CREATES AN ARRAY FOR localsObjectsVar. It doesn't touch
        // localsIntsVar because, at the first suspend(), var2 is UNINITALIZED. Nothing has been set to that variable slot.
        //
        //
        // The same thing applies to the operand stack. It doesn't make sense to create arrays for operand stack types that don't exist yet
        // at a continuation point, even though they may exist at other continuation points furhter down

        // Storage arrays from locals container
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, "Unpacking storage arrays for locals and operand stack from an Object[]"),
                GenericGenerators.mergeIf(localsSizes.getIntsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting locals ints from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(0),                                                                // [Object[], 0]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, localsIntsVar.getType().getInternalName()),    // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, localsIntsVar.getIndex()),                         // []
                }),
                GenericGenerators.mergeIf(localsSizes.getFloatsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting locals floats from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(1),                                                                // [Object[], 1]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, localsFloatsVar.getType().getInternalName()),  // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, localsFloatsVar.getIndex()),                       // []
                }),
                GenericGenerators.mergeIf(localsSizes.getLongsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting locals longs from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(2),                                                                // [Object[], 2]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, localsLongsVar.getType().getInternalName()),   // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, localsLongsVar.getIndex()),                        // []
                }),
                GenericGenerators.mergeIf(localsSizes.getDoublesSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting locals doubles from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(3),                                                                // [Object[], 3]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, localsDoublesVar.getType().getInternalName()), // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, localsDoublesVar.getIndex()),                      // []
                }),
                GenericGenerators.mergeIf(localsSizes.getObjectsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting locals objects from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(4),                                                                // [Object[], 4]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, localsObjectsVar.getType().getInternalName()), // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, localsObjectsVar.getIndex()),                      // []
                })
        );
    }
    
    public static InsnList unpackOperandStackStorageArrays(DebugGenerators.MarkerType markerType, Frame<BasicValue> frame, VariableTable.Variable containerVar,
                                                           StorageVariables operandStackStorageVars) {
        Validate.notNull(markerType);
        Validate.notNull(containerVar);
        Validate.notNull(operandStackStorageVars);
        
        VariableTable.Variable stackIntsVar = operandStackStorageVars.getIntStorageVar();
        VariableTable.Variable stackFloatsVar = operandStackStorageVars.getFloatStorageVar();
        VariableTable.Variable stackLongsVar = operandStackStorageVars.getLongStorageVar();
        VariableTable.Variable stackDoublesVar = operandStackStorageVars.getDoubleStorageVar();
        VariableTable.Variable stackObjectsVar = operandStackStorageVars.getObjectStorageVar();
        
        StorageSizes stackSizes = OperandStackStateGenerators.computeSizes(frame, 0, frame.getStackSize());
        
        // Why are we using size > 0 vs checking to see if var != null?
        //
        // REMEMBER THAT the analyzer will determine the variable slots to create for storage array based on its scan of EVERY
        // continuation/suspend point in the method. Imagine the method that we're instrumenting is this...
        //
        // public void example(SuspendableContext c, String arg1) {
        //     String var1 = "hi";
        //     c.suspend();     
        //
        //     System.out.println(var1);
        //     int var2 = 5;
        //     c.suspend();
        //
        //     System.out.println(var1 + var2);
        // }
        //
        // There are two continuation/suspend points. The analyzer determines that method will need to assign variable slots for
        // localsObjectsVar+localsIntsVar. All the other locals vars will be null.
        //
        // If we ended up using var != null instead of size > 0, things would mess up on the first suspend(). The only variable initialized
        // at the first suspend is var1. As such, LocalStateGenerator ONLY CREATES AN ARRAY FOR localsObjectsVar. It doesn't touch
        // localsIntsVar because, at the first suspend(), var2 is UNINITALIZED. Nothing has been set to that variable slot.
        //
        //
        // The same thing applies to the operand stack. It doesn't make sense to create arrays for operand stack types that don't exist yet
        // at a continuation point, even though they may exist at other continuation points furhter down

        // Storage arrays from locals container
        return GenericGenerators.merge(
                DebugGenerators.debugMarker(markerType, "Unpacking storage arrays for operand stack from an Object[]"),
                GenericGenerators.mergeIf(stackSizes.getIntsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting stack ints from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(5),                                                                // [Object[], 5]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, stackIntsVar.getType().getInternalName()),     // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, stackIntsVar.getIndex()),                          // []
                }),
                GenericGenerators.mergeIf(stackSizes.getFloatsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting stack floats from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(6),                                                                // [Object[], 6]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, stackFloatsVar.getType().getInternalName()),   // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, stackFloatsVar.getIndex()),                        // []
                }),
                GenericGenerators.mergeIf(stackSizes.getLongsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting stack longs from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(7),                                                                // [Object[], 7]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, stackLongsVar.getType().getInternalName()),    // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, stackLongsVar.getIndex()),                         // []
                }),
                GenericGenerators.mergeIf(stackSizes.getDoublesSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting stack doubles from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(8),                                                                // [Object[], 8]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, stackDoublesVar.getType().getInternalName()),  // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, stackDoublesVar.getIndex()),                       // []
                }),
                GenericGenerators.mergeIf(stackSizes.getObjectsSize() > 0, () -> new Object[] {
                    DebugGenerators.debugMarker(markerType, "Getting stack objects from to container"),
                    new VarInsnNode(Opcodes.ALOAD, containerVar.getIndex()),                           // [Object[]]
                    new LdcInsnNode(9),                                                                // [Object[], 9]
                    new InsnNode(Opcodes.AALOAD),                                                      // [val]
                    new TypeInsnNode(Opcodes.CHECKCAST, stackObjectsVar.getType().getInternalName()),  // [val] REQ BY JVM SO TYPE IS KNOWN
                    new VarInsnNode(Opcodes.ASTORE, stackObjectsVar.getIndex()),                       // []
                })
        );
    }
}
