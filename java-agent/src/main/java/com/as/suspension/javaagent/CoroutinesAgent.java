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
package com.as.suspension.javaagent;

import com.as.suspension.instrumenter.InstrumentationResult;
import com.as.suspension.instrumenter.InstrumentationSettings;
import com.as.suspension.instrumenter.Instrumenter;
import com.as.suspension.instrumenter.asm.ClassResourceClassInformationRepository;
import com.as.suspension.instrumenter.generators.DebugGenerators.MarkerType;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;

/**
 * Java Agent that instruments coroutines.
 * @author Kasra Faghihi
 */
public final class CoroutinesAgent {

    private CoroutinesAgent() {
        // do nothing
    }
    
    /**
     * Java agent premain.
     * @param agentArgs args passed in to agent
     * @param inst instrumentation for agent
     * @throws NullPointerException if {@code inst} is {@code null}
     * @throws IllegalArgumentException if {@code agentArgs} is present but not in the expected format, or if the passed in arguments were
     * not parseable
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        // How do agent args work? http://stackoverflow.com/questions/23287228/how-do-i-pass-arguments-to-a-java-instrumentation-agent
        // e.g. java -javaagent:/path/to/agent.jar=argumentstring
        
        MarkerType markerType = MarkerType.NONE;
        boolean debugMode = false;
        boolean autoSerializable = true;
        if (agentArgs != null && !agentArgs.isEmpty()) {
            String[] splitArgs = agentArgs.split(",");
            for (String splitArg : splitArgs) {
                String[] keyVal = splitArg.split("=", 2);
                if (keyVal.length != 2) {
                    throw new IllegalArgumentException("Unrecognized arg passed to Coroutines Java agent: " + splitArg);
                }
                
                String key = keyVal[0];
                String val = keyVal[1];

                switch (key) {
                    case "markerType":
                        try {
                            markerType = MarkerType.valueOf(val);
                        } catch (IllegalArgumentException iae) {
                            throw new IllegalArgumentException("Unable to parse marker type -- must be one of the following: "
                                    + Arrays.toString(MarkerType.values()), iae);
                        }
                        break;
                    case "debugMode":
                        if (val.equalsIgnoreCase("true")) {
                            debugMode = true;
                        } else if (val.equalsIgnoreCase("false")) {
                            debugMode = false;
                        } else {
                            throw new IllegalArgumentException("Unable to parse debug mode -- must be true or false");
                        }
                        break;
                    case "autoSerializable":
                        if (val.equalsIgnoreCase("true")) {
                            autoSerializable = true;
                        } else if (val.equalsIgnoreCase("false")) {
                            autoSerializable = false;
                        } else {
                            throw new IllegalArgumentException("Unable to parse debug mode -- must be true or false");
                        }
                        break;                        
                    default:
                        throw new IllegalArgumentException("Unrecognized arg passed to Coroutines Java agent: " + keyVal);
                }
            }
        }
        
        inst.addTransformer(new CoroutinesClassFileTransformer(markerType, debugMode, autoSerializable));
    }
    
    private static final class CoroutinesClassFileTransformer implements ClassFileTransformer {
        private final MarkerType markerType;
        private final boolean debugMode;
        private final boolean autoSerializable;

        CoroutinesClassFileTransformer(MarkerType markerType, boolean debugMode, boolean autoSerializable) {
            if (markerType == null) {
                throw new NullPointerException();
            }

            this.markerType = markerType;
            this.debugMode = debugMode;
            this.autoSerializable = autoSerializable;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                byte[] classfileBuffer) throws IllegalClassFormatException {
//            ClassReader cr = new ClassReader(classfileBuffer);
//            ClassNode classNode = new SimpleClassNode();
//            cr.accept(classNode, 0);
//            String classNameFromBytes = classNode.name;
            
            // If class is internal to the coroutines user project, don't instrument them
            //   FYI: If the class being transformed is a lambda, className will show up as null.
            if (className == null || className.startsWith("com/as/suspension/user/")) {
                return null;
            }
            
            // If loader is null, don't attempt instrumentation (this is a core class?)
            if (loader == null) {
                return null;
            }
            
//            System.out.println(className + " " + (loader == null));
            
            try {
                InstrumentationSettings settings = new InstrumentationSettings(markerType, debugMode, autoSerializable);
                Instrumenter instrumenter = new Instrumenter(new ClassResourceClassInformationRepository(loader));
                InstrumentationResult result = instrumenter.instrument(classfileBuffer, settings);
                return result.getInstrumentedClass();
            } catch (Throwable e) {
                System.err.println("FAILED TO INSTRUMENT: " + e);
                return null;
            }
        }
        
    }
}
