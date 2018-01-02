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
import com.as.suspension.user.LockState;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class LockVariables {
    private final VariableTable.Variable lockStateVar;
    private final VariableTable.Variable counterVar;
    private final VariableTable.Variable arrayLenVar;

    LockVariables(
            VariableTable.Variable lockStateVar,
            VariableTable.Variable counterVar,
            VariableTable.Variable arrayLenVar) {
        // vars can be null -- they'll be null if the analyzer determined tehy aren't required
        Validate.isTrue(lockStateVar == null || lockStateVar.getType().equals(Type.getType(LockState.class)));
        Validate.isTrue(counterVar == null || counterVar.getType().equals(Type.INT_TYPE));
        Validate.isTrue(arrayLenVar == null || arrayLenVar.getType().equals(Type.INT_TYPE));

        this.lockStateVar = lockStateVar;
        this.counterVar = counterVar;
        this.arrayLenVar = arrayLenVar;
    }

    public VariableTable.Variable getLockStateVar() {
        return lockStateVar;
    }

    public VariableTable.Variable getCounterVar() {
        return counterVar;
    }

    public VariableTable.Variable getArrayLenVar() {
        return arrayLenVar;
    }
}
