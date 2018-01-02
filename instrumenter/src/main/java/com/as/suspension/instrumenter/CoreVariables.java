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
import com.as.suspension.user.SuspendableContext;
import com.as.suspension.user.MethodState;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class CoreVariables {
    private final VariableTable.Variable continuationArgVar;
    private final VariableTable.Variable methodStateVar;
    
    CoreVariables(
            VariableTable.Variable continuationArgVar,
            VariableTable.Variable methodStateVar) {
        Validate.notNull(continuationArgVar);
        Validate.notNull(methodStateVar);
        Validate.isTrue(continuationArgVar.getType().equals(Type.getType(SuspendableContext.class)));
        Validate.isTrue(methodStateVar.getType().equals(Type.getType(MethodState.class)));
        
        this.continuationArgVar = continuationArgVar;
        this.methodStateVar = methodStateVar;
    }

    public VariableTable.Variable getContinuationArgVar() {
        return continuationArgVar;
    }

    public VariableTable.Variable getMethodStateVar() {
        return methodStateVar;
    }
}
