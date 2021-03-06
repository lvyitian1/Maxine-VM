/*
 * Copyright (c) 2017, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package jtt.lang;

/*
 * @Harness: java
 * @Runs: 5.0d = 5.0d;
 * @Runs: -5.0d = 5.0d;
 * @Runs: 0.0d = 0.0d;
 * @Runs: -0.0d = 0.0d;
 * @Runs: `java.lang.Double.NEGATIVE_INFINITY = `java.lang.Double.POSITIVE_INFINITY;
 * @Runs: `java.lang.Double.POSITIVE_INFINITY = `java.lang.Double.POSITIVE_INFINITY;
 * @Runs: `java.lang.Double.NaN = !jtt.lang.Math_abs$NaN;
 */
public class Math_abs {

    public static class NaN extends Throwable {
        private static final long serialVersionUID = -5511213444570671005L;
    }

    public static double test(double arg) throws NaN {

        double v = Math.abs(arg);
        if (Double.isNaN(v)) {
            // NaN can't be tested against itself
            throw new NaN();
        }
        return v;
    }
}
