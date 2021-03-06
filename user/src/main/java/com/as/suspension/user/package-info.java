/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
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

/**
 * User-level classes for coroutines. The entry-point of your coroutine should be a class that implements
 * {@link com.as.suspension.user.Suspendable}. To run your coroutine, use {@link com.as.suspension.user.CoroutineRunner}.
 * <p>
 * A simple example of a coroutine:
 * <pre>
 * import SuspendableContext;
 * import Suspendable;
 * 
 * public class SimpleTest implements Suspendable {
 * 
 *     public void run(SuspendableContext c) {
 *         System.out.println("started");
 *         for (int i = 0; i &lt; 10; i++) {
 *             echo(c, i);
 *         }
 *     }
 * 
 *     private void echo(SuspendableContext c, int x) {
 *         System.out.println(x);
 *         c.suspend();
 *     }
 * }
 * </pre>
 * 
 * @author Kasra Faghihi
 */
package com.as.suspension.user;
