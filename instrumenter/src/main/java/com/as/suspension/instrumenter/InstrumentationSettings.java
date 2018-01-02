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

import com.as.suspension.instrumenter.generators.DebugGenerators;

import java.io.Serializable;
import org.apache.commons.lang3.Validate;

/**
 * Instrumentation settings.
 * @author Kasra Faghihi
 */
public final class InstrumentationSettings {
    private final DebugGenerators.MarkerType markerType;
    private final boolean debugMode;
    private final boolean autoSerializable;

    /**
     * Constructs a {@link InstrumentationSettings} object.
     * @param markerType marker type
     * @param debugMode debug mode
     * @param autoSerializable auto-serializable
     * @throws NullPointerException if any argument is {@code null}
     */
    public InstrumentationSettings(DebugGenerators.MarkerType markerType, boolean debugMode, boolean autoSerializable) {
        Validate.notNull(markerType);
        this.markerType = markerType;
        this.debugMode = debugMode;
        this.autoSerializable = autoSerializable;
    }

    /**
     * Get marker type. Depending on the marker type used, markers will be added to the instrumented code that explains what each portion of
     * the instrumented code is doing. This is useful for debugging the instrumentation logic (if the instrumented code is bugged).
     * @return marker type
     */
    public DebugGenerators.MarkerType getMarkerType() {
        return markerType;
    }

    /**
     * Get debug mode. Debug mode adds extra instrumentation code to the class such that the instrumented method's state is viewable when
     * you run the instrumented code through a debugger (e.g. the debugger in Netbeans/Eclipse/IntelliJ).
     * @return debug mode
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Get auto-serializable. Auto-serializable will automatically mark the classes of methods intended to run as part of a coroutine as
     * {@link Serializable} and provide a assign a default {@code serialVersionUID}. This is vital for the serialization feature (if the
     * user uses the default serializer). If the classes don't extend {@link Serializable}, they'll get an exception when serializing it.
     * Also, if they don't hardcode a {@code serialVersionUID}, any change to the class will cause deserialization of previous versions to
     * fail (because the {@code serialVersionUID} internally generated by Java will change between the old and new version). 
     * @return auto-serializable
     */
    public boolean isAutoSerializable() {
        return autoSerializable;
    }

}