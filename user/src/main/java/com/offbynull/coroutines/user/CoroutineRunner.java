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
package com.offbynull.coroutines.user;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Used to execute a {@link Suspendable}. All {@link Suspendable}s must be executed through this class.
 * @author Kasra Faghihi
 */
public final class CoroutineRunner implements Serializable {
    private static final long serialVersionUID = 5L;
    
    private Suspendable suspendable;
    private SuspendableContext suspendableContext;

    /**
     * Constructs a {@link CoroutineRunner} object.
     * @param suspendable suspendable to run
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoroutineRunner(Suspendable suspendable) {
        if (suspendable == null) {
            throw new NullPointerException();
        }
        this.suspendable = suspendable;
        this.suspendableContext = new SuspendableContext();
    }
    
    CoroutineRunner(Suspendable suspendable, SuspendableContext suspendableContext) {
        if (suspendable == null || suspendableContext == null) {
            throw new NullPointerException();
        }
        this.suspendable = suspendable;
        this.suspendableContext = suspendableContext;
    }

    /**
     * Starts/resumes execution of this suspendable. If the suspendable being executed reaches a suspension point (meaning that the method calls
     * {@link SuspendableContext#suspend() }), this method will return {@code true}. If the suspendable has finished executing, this method will
     * return {@code false}.
     * <p>
     * Calling this method again after the suspendable has finished executing will restart the suspendable.
     * @return {@code false} if execution has completed (the method has return), {@code true} if execution was suspended.
     * @throws CoroutineException an exception occurred during execution of this suspendable, the saved execution stack and object state may
     * be out of sync at this point (meaning that unless you know what you're doing, you should not call {@link CoroutineRunner#execute() }
     * again)
     */
    public boolean execute(Object... args) {
        try {
            if(suspendableContext.getArgumentFrames() == null){
                suspendableContext.setArgumentFrames(new ArrayList<ArgumentFrame>());
            }
            suspendableContext.getArgumentFrames().add(new ArgumentFrame(args));
            suspendable.run(suspendableContext);
            suspendableContext.successExecutionCycle();
        } catch (Exception e) {
            suspendableContext.failedExecutionCycle();
            throw new CoroutineException("Exception thrown during execution", e);
        }
        
        // if mode was not set to SAVING after return, it means the method finished executing
        if (suspendableContext.getMode() != SuspendableContext.MODE_SAVING) {
            suspendableContext.reset(); // clear methodstates + set to normal
            return false;
        } else {
            suspendableContext.setMode(SuspendableContext.MODE_LOADING); // set to loading for next invokation
            return true;
        }
    }

    /**
     * Get the context. Accessible via the {@link SuspendableContext} object that gets used by this suspendable.
     * @return context context
     */
    public Object getContext() {
        return suspendableContext.getContext();
    }

    /**
     * Set the context. Accessible via the {@link SuspendableContext} object that gets used by this suspendable.
     * @param context context
     */
    public void setContext(Object context) {
        suspendableContext.setContext(context);
    }

    /**
     * Get the suspendable assigned to this runner.
     * @return suspendable assigned to this runner
     */
    public Suspendable getSuspendable() {
        return suspendable;
    }

    SuspendableContext getSuspendableContext() {
        return suspendableContext;
    }
}
