/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package jtt.micro;

/*
 * @Harness: java
 * @Runs: (0, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 1f;
 * @Runs: (1, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 2f;
 * @Runs: (2, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 3f;
 * @Runs: (3, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 4f;
 * @Runs: (4, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 5f;
 * @Runs: (5, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 6f;
 * @Runs: (6, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 7f;
 * @Runs: (7, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 8f;
 * @Runs: (8, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f) = 9f
 */
public class BigFloatParams02 {

    public static float test(int choice, float p0, float p1, float p2, float p3, float p4, float p5, float p6, float p7, float p8) {
        switch (choice) {
            case 0:
                return p0;
            case 1:
                return p1;
            case 2:
                return p2;
            case 3:
                return p3;
            case 4:
                return p4;
            case 5:
                return p5;
            case 6:
                return p6;
            case 7:
                return p7;
            case 8:
                return p8;
        }
        return 42;
    }
}
