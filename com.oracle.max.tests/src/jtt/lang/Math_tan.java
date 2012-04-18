/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jtt.lang;

/*
 * @Harness: java
 * @Runs: `java.lang.Double.NaN = !jtt.lang.Math_tan$NaN;
 * @Runs: `java.lang.Double.NEGATIVE_INFINITY = !jtt.lang.Math_tan$NaN;
 * @Runs: `java.lang.Double.POSITIVE_INFINITY = !jtt.lang.Math_tan$NaN;
 * @Runs: -0.0d = -0.0d;
 * @Runs: 0.0d = 0.0d;
 */
public class Math_tan {

    public static class NaN extends Throwable { }

    public static double test(double arg) throws NaN {
        double v = Math.tan(arg);
        if (Double.isNaN(v)) {
            // NaN can't be tested against itself
            throw new NaN();
        }
        return v;
    }
}
