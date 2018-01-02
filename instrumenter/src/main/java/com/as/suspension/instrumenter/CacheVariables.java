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
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class CacheVariables {
    private final VariableTable.Variable intReturnCacheVar;
    private final VariableTable.Variable longReturnCacheVar;
    private final VariableTable.Variable floatReturnCacheVar;
    private final VariableTable.Variable doubleReturnCacheVar;
    private final VariableTable.Variable objectReturnCacheVar;
    private final VariableTable.Variable throwableCacheVar;
    
    CacheVariables(
            VariableTable.Variable intReturnCacheVar,
            VariableTable.Variable longReturnCacheVar,
            VariableTable.Variable floatReturnCacheVar,
            VariableTable.Variable doubleReturnCacheVar,
            VariableTable.Variable objectReturnCacheVar,
            VariableTable.Variable throwableCacheVar) {
        // cache vars CAN BE NULL -- if they weren't created it means it was determined that it wasn't required
        Validate.isTrue(intReturnCacheVar == null || intReturnCacheVar.getType().equals(Type.INT_TYPE));
        Validate.isTrue(longReturnCacheVar == null || longReturnCacheVar.getType().equals(Type.LONG_TYPE));
        Validate.isTrue(floatReturnCacheVar == null || floatReturnCacheVar.getType().equals(Type.FLOAT_TYPE));
        Validate.isTrue(doubleReturnCacheVar == null || doubleReturnCacheVar.getType().equals(Type.DOUBLE_TYPE));
        Validate.isTrue(objectReturnCacheVar == null || objectReturnCacheVar.getType().equals(Type.getType(Object.class)));
        Validate.isTrue(throwableCacheVar == null || throwableCacheVar.getType().equals(Type.getType(Throwable.class)));

        this.intReturnCacheVar = intReturnCacheVar;
        this.longReturnCacheVar = longReturnCacheVar;
        this.floatReturnCacheVar = floatReturnCacheVar;
        this.doubleReturnCacheVar = doubleReturnCacheVar;
        this.objectReturnCacheVar = objectReturnCacheVar;
        
        this.throwableCacheVar = throwableCacheVar;
    }

    public VariableTable.Variable getIntReturnCacheVar() {
        Validate.validState(intReturnCacheVar != null, "Return cache variable of type not assigned");
        return intReturnCacheVar;
    }

    public VariableTable.Variable getLongReturnCacheVar() {
        Validate.validState(longReturnCacheVar != null, "Return cache variable of type not assigned");
        return longReturnCacheVar;
    }

    public VariableTable.Variable getFloatReturnCacheVar() {
        Validate.validState(floatReturnCacheVar != null, "Return cache variable of type not assigned");
        return floatReturnCacheVar;
    }

    public VariableTable.Variable getDoubleReturnCacheVar() {
        Validate.validState(doubleReturnCacheVar != null, "Return cache variable of type not assigned");
        return doubleReturnCacheVar;
    }

    public VariableTable.Variable getObjectReturnCacheVar() {
        Validate.validState(objectReturnCacheVar != null, "Return cache variable of type not assigned");
        return objectReturnCacheVar;
    }

    public VariableTable.Variable getThrowableCacheVar() {
        Validate.validState(throwableCacheVar != null, "Throwable cache variable of type not assigned");
        return throwableCacheVar;
    }
    
    public VariableTable.Variable getReturnCacheVar(Type type) {
        Validate.notNull(type);

        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                return getIntReturnCacheVar();
            case Type.LONG:
                return getLongReturnCacheVar();
            case Type.FLOAT:
                return getFloatReturnCacheVar();
            case Type.DOUBLE:
                return getDoubleReturnCacheVar();
            case Type.ARRAY:
            case Type.OBJECT:
                return getObjectReturnCacheVar();
            case Type.VOID:
                return null;
            default:
                throw new IllegalArgumentException("Bad type");
        }
    }
}
