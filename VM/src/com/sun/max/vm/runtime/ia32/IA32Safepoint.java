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
package com.sun.max.vm.runtime.ia32;

import com.sun.max.annotate.*;
import com.sun.max.asm.*;
import com.sun.max.asm.ia32.*;
import com.sun.max.asm.ia32.complete.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;

/**
 * @author Bernd Mathiske
 */
public final class IA32Safepoint extends Safepoint {

    public IA32Safepoint(VMConfiguration vmConfiguration) {
        super(vmConfiguration);
    }

    @INLINE(override = true)
    @Override
    public IA32GeneralRegister32 latchRegister() {
        return null;
    }

    @Override
    protected byte[] createCode() {
        final IA32Assembler asm = new IA32Assembler(0);
        try {
            asm.nop();
            return asm.toByteArray();
        } catch (AssemblyException assemblyException) {
            throw ProgramError.unexpected("could not assemble safepoint code");
        }
    }

    @Override
    public Pointer getInstructionPointer(Pointer trapState) {
        throw Problem.unimplemented();
    }
    @Override
    public Pointer getStackPointer(Pointer trapState, TargetMethod targetMethod) {
        throw Problem.unimplemented();
    }
    @Override
    public Pointer getFramePointer(Pointer trapState, TargetMethod targetMethod) {
        throw Problem.unimplemented();
    }
    @Override
    public Pointer getSafepointLatch(Pointer trapState) {
        throw Problem.unimplemented();
    }
    @Override
    public void setSafepointLatch(Pointer trapState, Pointer value) {
        throw Problem.unimplemented();
    }
    @Override
    public Pointer getRegisterState(Pointer trapState) {
        throw Problem.unimplemented();
    }
    @Override
    public int getTrapNumber(Pointer trapState) {
        throw Problem.unimplemented();
    }
}
