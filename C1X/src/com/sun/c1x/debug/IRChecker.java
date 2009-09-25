/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.c1x.debug;

import java.util.*;

import com.sun.c1x.bytecode.*;
import com.sun.c1x.ci.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.ri.*;
import com.sun.c1x.util.Util;

/**
 * The <code>IRChecker</code> class walks over the IR graph and checks
 * that each instruction has the appropriate type for its inputs and output,
 * as well as other structural properties of the IR graph.
 *
 * @author Marcelo Cintra
 * @author Ben L. Titzer
 */
public class IRChecker extends ValueVisitor {

    /**
     * The <code>IRCheckException</code> class is thrown when the IRChecker detects
     * a problem with the IR.
     *
     * @author Marcelo Cintra
     */
    public static class IRCheckException extends RuntimeException {
        public static final long serialVersionUID = 8974598793158772L;

        public IRCheckException(String phase, String msg) {
            super("IRCheck error " + phase + ": " + msg);
        }
    }

    private final IR ir;
    private final String phase;
    private final HashMap<Integer, BlockBegin> idMap = new HashMap<Integer, BlockBegin>();
    private final BasicValueChecker basicChecker = new BasicValueChecker();

    /**
     * Creates a new IRChecker for the specified IR.
     * @param ir the IR to check
     * @param phase the phase of compilation when verification is being run
     */
    public IRChecker(IR ir, String phase) {
        this.ir = ir;
        this.phase = phase;
    }

    public void check() {
        ir.startBlock.iterateAnyOrder(new CheckBlock(), false);
        ir.startBlock.iterateAnyOrder(new CheckReachable(), true);
        ir.startBlock.iterateAnyOrder(new CheckValues(), false);
    }

    private class CheckBlock implements BlockClosure {
        public void apply(BlockBegin block) {
            // check every instruction in the block top to bottom
            BlockBegin b = idMap.get(block.blockID);
            if (b != null && b != block) {
                fail("Block id is not unique " + block + " and " + b);
            }
            idMap.put(block.blockID, block);
            Instruction instr = block;
            Instruction prev = block;
            while (instr != null) {
                if (instr instanceof BlockEnd) {
                    assertNull(instr.next(), "BlockEnd should not have next: " + instr);
                }
                prev = instr;
                instr = instr.next();
            }
            if (!(prev instanceof BlockEnd)) {
                fail("Block should end with a BlockEnd " + block);
            }
            if (prev != block.end()) {
                fail("Block refers to wrong block end " + block);
            }
            checkBlockEnd(block.end());
        }
    }

    private class CheckReachable implements BlockClosure {
        public void apply(BlockBegin block) {
            // check that the block was reachable in the first pass
            if (idMap.get(block.blockID) != block) {
                fail("Block is not reachable from start block: " + block);
            }
            List<BlockBegin> linearScanOrder = ir.linearScanOrder();
            if (linearScanOrder != null) {
                if (!linearScanOrder.contains(block)) {
                    fail("Block not in linear scan list: " + block);
                }
            }
        }
    }

    private class CheckValues implements BlockClosure {
        public void apply(BlockBegin block) {
            Instruction instr = block;
            while (instr != null) {
                basicChecker.apply(instr);
                instr.allValuesDo(basicChecker);
                instr.accept(IRChecker.this);
                instr = instr.next();
            }
        }
    }

    /**
     * Checks the basic types of incoming instructions and the type of this instruction
     * match the types expected by the opcode.
     * @param i the ArithmeticOp instruction to be verified
     */
    @Override
    public void visitArithmeticOp(ArithmeticOp i) {
        Value x = i.x();
        Value y = i.y();

        switch (i.opcode()) {
            case Bytecodes.IADD:
            case Bytecodes.ISUB:
            case Bytecodes.IMUL:
            case Bytecodes.IDIV:
            case Bytecodes.IREM:
                assertBasicType(i, CiKind.Int);
                assertBasicType(x, CiKind.Int);
                assertBasicType(y, CiKind.Int);
                break;

            case Bytecodes.LADD:
            case Bytecodes.LSUB:
            case Bytecodes.LMUL:
            case Bytecodes.LDIV:
            case Bytecodes.LREM:
                assertBasicType(i, CiKind.Long);
                assertBasicType(x, CiKind.Long);
                assertBasicType(y, CiKind.Long);
                break;

            case Bytecodes.FADD:
            case Bytecodes.FSUB:
            case Bytecodes.FMUL:
            case Bytecodes.FDIV:
            case Bytecodes.FREM:
                assertBasicType(i, CiKind.Float);
                assertBasicType(x, CiKind.Float);
                assertBasicType(y, CiKind.Float);
                break;

            case Bytecodes.DADD:
            case Bytecodes.DSUB:
            case Bytecodes.DMUL:
            case Bytecodes.DDIV:
            case Bytecodes.DREM:
                assertBasicType(i, CiKind.Double);
                assertBasicType(x, CiKind.Double);
                assertBasicType(y, CiKind.Double);
                break;

            default:
                fail("Arithmetic operation instruction has an illegal opcode");
        }
    }

    /**
     * Checks the basic types of incoming instructions and the type of this instruction
     * match the types expected by the opcode.
     * @param i the logic instruction to be verified
     */
    @Override
    public void visitLogicOp(LogicOp i) {
        Value x = i.x();
        Value y = i.y();

        switch (i.opcode()) {
            case Bytecodes.IAND:
            case Bytecodes.IOR:
            case Bytecodes.IXOR:
                assertBasicType(i, CiKind.Int);
                assertBasicType(x, CiKind.Int);
                assertBasicType(y, CiKind.Int);
                break;
            case Bytecodes.LAND:
            case Bytecodes.LOR:
            case Bytecodes.LXOR:
                assertBasicType(i, CiKind.Long);
                assertBasicType(x, CiKind.Long);
                assertBasicType(y, CiKind.Long);
                break;
            default:
                fail("Logic operation instruction has an illegal opcode");
        }
    }

    /**
     * Checks the basic types of the incoming instruction and the type of this instruction
     * match the types expected by the opcode.
     * @param i the NegateOp instruction to be verified
     */
    @Override
    public void visitNegateOp(NegateOp i) {
        assertBasicType(i, i.x().type());
    }

    /**
     * Checks the basic types of incoming instructions and the type of this instruction
     * match the types expected by the opcode.
     * @param i the CompareOp instruction to be verified
     */
    @Override
    public void visitCompareOp(CompareOp i) {
        Value x = i.x();
        Value y = i.y();

        switch (i.opcode()) {
            case Bytecodes.LCMP:
                assertBasicType(i, CiKind.Int);
                assertBasicType(x, CiKind.Long);
                assertBasicType(y, CiKind.Long);
                break;
            case Bytecodes.FCMPG:
            case Bytecodes.FCMPL:
                assertBasicType(i, CiKind.Int);
                assertBasicType(x, CiKind.Float);
                assertBasicType(y, CiKind.Float);
                break;
            case Bytecodes.DCMPG:
            case Bytecodes.DCMPL:
                assertBasicType(i, CiKind.Int);
                assertBasicType(x, CiKind.Double);
                assertBasicType(y, CiKind.Double);
                break;
            default:
                fail("Illegal CompareOp opcode");
        }
    }

    /**
     * Typechecks a IfOp instruction.
     * @param i the IfOp instruction to be verified
     */
    @Override
    public void visitIfOp(IfOp i) {
        if (i.opcode() != Bytecodes.ILLEGAL) {
            fail("Opcode for IfOp instruction must be ILLEGAL");
        }

        assertLegal(i);
        if (i.x().type() != i.y().type()) {
            fail("Operands to IfOp do not have the same basic type");
        }
        assertBasicType(i, i.trueValue().type().meet(i.falseValue().type()));
    }

    /**
     * Checks the basic types of incoming instructions and the type of this instruction
     * match the types expected by the opcode.
     * @param i the ShiftOp instruction to be verified
     */
    @Override
    public void visitShiftOp(ShiftOp i) {
        switch (i.opcode()) {
            case Bytecodes.ISHL:
            case Bytecodes.ISHR:
            case Bytecodes.IUSHR:
                assertBasicType(i, CiKind.Int);
                assertBasicType(i.x(), CiKind.Int);
                assertBasicType(i.y(), CiKind.Int);
                break;

            case Bytecodes.LSHL:
            case Bytecodes.LSHR:
            case Bytecodes.LUSHR:
                assertBasicType(i, CiKind.Long);
                assertBasicType(i.x(), CiKind.Long);
                assertBasicType(i.y(), CiKind.Int);
                break;
            default:
                fail("Illegal ShiftOp opcode");
        }
    }

    /**
     * Checks the basic types of incoming instruction and the type of this instruction
     * match the types expected by the opcode.
     * @param i the convert instruction to be verified
     */
    @Override
    public void visitConvert(Convert i) {
        switch (i.opcode()) {
            case Bytecodes.I2L:
                assertBasicType(i, CiKind.Long);
                assertBasicType(i.value(), CiKind.Int);
                break;
            case Bytecodes.I2F:
                assertBasicType(i, CiKind.Float);
                assertBasicType(i.value(), CiKind.Int);
                break;
            case Bytecodes.I2D:
                assertBasicType(i, CiKind.Double);
                assertBasicType(i.value(), CiKind.Int);
                break;
            case Bytecodes.I2B:
            case Bytecodes.I2C:
            case Bytecodes.I2S:
                assertBasicType(i, CiKind.Int);
                assertBasicType(i.value(), CiKind.Int);
                break;

            case Bytecodes.L2I:
                assertBasicType(i, CiKind.Int);
                assertBasicType(i.value(), CiKind.Long);
                break;
            case Bytecodes.L2F:
                assertBasicType(i, CiKind.Float);
                assertBasicType(i.value(), CiKind.Long);
                break;
            case Bytecodes.L2D:
                assertBasicType(i, CiKind.Double);
                assertBasicType(i.value(), CiKind.Long);
                break;

            case Bytecodes.F2I:
                assertBasicType(i, CiKind.Int);
                assertBasicType(i.value(), CiKind.Float);
                break;
            case Bytecodes.F2L:
                assertBasicType(i, CiKind.Long);
                assertBasicType(i.value(), CiKind.Float);
                break;
            case Bytecodes.F2D:
                assertBasicType(i, CiKind.Double);
                assertBasicType(i.value(), CiKind.Float);
                break;

            case Bytecodes.D2I:
                assertBasicType(i, CiKind.Int);
                assertBasicType(i.value(), CiKind.Double);
                break;
            case Bytecodes.D2L:
                assertBasicType(i, CiKind.Long);
                assertBasicType(i.value(), CiKind.Double);
                break;
            case Bytecodes.D2F:
                assertBasicType(i, CiKind.Float);
                assertBasicType(i.value(), CiKind.Double);
                break;
            default:
                fail("invalid opcode in Convert");
        }
    }

    /**
     * Checks that the incoming instruction is an object, and this instruction is an object.
     * @param i the null check instruction to be verified
     */
    @Override
    public void visitNullCheck(NullCheck i) {
        if (i.object() == null) {
            fail("There is no instruction producing the object to check against null");
        }
        assertBasicType(i, CiKind.Object);
        assertBasicType(i.object(), CiKind.Object);
    }

    /**
     * Checks the incoming object instruction, if any, is of type object and that
     * this instruction has the same basic type as the field's basic type.
     * @param i the LoadField instruction to be verified
     */
    @Override
    public void visitLoadField(LoadField i) {
        assertBasicType(i, i.field().basicType().stackType());
        Value object = i.object();
        if (object != null) {
            assertBasicType(object, CiKind.Object);
            assertInstanceType(object.declaredType());
            assertInstanceType(object.exactType());
        } else if (!i.isStatic()) {
            fail("LoadField of instance field should not have null object");
        }
    }

    /**
     * Typechecks a StoreField instruction.
     * @param i the StoreField instruction to be verified
     */
    @Override
    public void visitStoreField(StoreField i) {
        assertBasicType(i.value(), i.field().basicType().stackType());
        Value object = i.object();
        if (object != null) {
            assertBasicType(object, CiKind.Object);
            assertInstanceType(object.declaredType());
            assertInstanceType(object.exactType());
        } else if (!i.isStatic()) {
            fail("StoreField of instance field should not have null object");
        }
    }

    /**
     * Typechecks a LoadIndexed instruction.
     * @param i the LoadIndexed instruction to be verified
     */
    @Override
    public void visitLoadIndexed(LoadIndexed i) {
        assertBasicType(i.array(), CiKind.Object);
        assertBasicType(i.index(), CiKind.Int);
        assertBasicType(i, i.elementType().stackType());
        assertArrayType(i.array().exactType());
        assertArrayType(i.array().declaredType());
    }

    /**
     * Typechecks a StoreIndexed instruction.
     * @param i the StoreIndexed instruction to be verified
     */
    @Override
    public void visitStoreIndexed(StoreIndexed i) {
        assertBasicType(i.array(), CiKind.Object);
        assertBasicType(i.index(), CiKind.Int);
        assertBasicType(i.value(), i.elementType().stackType());
        assertBasicType(i, i.elementType().stackType());
        assertArrayType(i.array().exactType());
        assertArrayType(i.array().declaredType());
    }

    /**
     * Typechecks an ArrayLength instruction.
     * @param i the ArrayLength instruction to be verified
     */
    @Override
    public void visitArrayLength(ArrayLength i) {
        assertBasicType(i.array(), CiKind.Object);
        assertArrayType(i.array().exactType());
        assertArrayType(i.array().declaredType());
        assertBasicType(i, CiKind.Int);
    }

    /**
     * Typechecks a Constant instruction.
     * @param i the constant to be verified
     */
    @Override
    public void visitConstant(Constant i) {
        // do nothing.
    }

    /**
     * Typechecks an Exception Object instruction.
     * @param i the ExceptionObject instruction to be verified
     */
    @Override
    public void visitExceptionObject(ExceptionObject i) {
        assertBasicType(i, CiKind.Object);
    }

    /**
    * Typechecks a Local instruction.
    * @param i the Local instruction to be verified
    */
    @Override
    public void visitLocal(Local i) {
        if (i.javaIndex() < 0) {
            fail("Java index of Local instruction must be greater then or equal to zero");
        }
        assertLegal(i);
    }

    /**
    * Typechecks an OsrEntry instruction.
    * @param i the OsrEntry instruction to be verified
    */
    @Override
    public void visitOsrEntry(OsrEntry i) {
        // TODO: this type should probably be BasicType.Word in the future
        assertBasicType(i, CiKind.Jsr);
    }

    /**
     * Checks if the type of each operand in a Phi instruction is equivalent to
     * the type of the destination variable (or operand stack) slot.
     * @param i the arithmetic operation to be verified
     */
    @Override
    public void visitPhi(Phi i) {
        if (!i.isIllegal()) {
            checkPhi(i);
        }
    }

    private void checkPhi(Phi i) {
        BlockBegin block = i.block();
        if (idMap.get(block.blockID) != block) {
            fail("Phi refers to unreachable block " + i + " " + block);
        }
        // if the phi instruction corresponds to a local variable, checks if the local index is valid
        if (i.isLocal() && (i.localIndex() >= block.stateBefore().scope().method.maxLocals() || i.localIndex() < 0)) {
            fail("Phi refers to an invalid local variable");
        }
        for (int j = 0; j < i.operandCount(); j++) {
            assertBasicType(i.operandAt(j), i.type());
        }
    }

    /**
     * Typechecks a RoundFP instruction.
     * @param i the RoundFP instruction to be verified
     */
    @Override
    public void visitRoundFP(RoundFP i) {
        switch (i.type()) {
            case Float:
                assertBasicType(i.value(), CiKind.Float);
                break;
            case Double:
                assertBasicType(i.value(), CiKind.Double);
                break;
            default:
                fail("type of RoundFP must be floating point");
        }
    }

    /**
     * Typechecks a MonitorEnter instruction.
     * @param i the MonitorEnter instruction to be verified
     */
    @Override
    public void visitMonitorEnter(MonitorEnter i) {
        assertBasicType(i, CiKind.Illegal);
        assertBasicType(i.object(), CiKind.Object);
    }

    /**
     * Typechecks a MonitorExit instruction.
     * @param i the MonitorExit instruction to be verified
     */
    @Override
    public void visitMonitorExit(MonitorExit i) {
        assertBasicType(i, CiKind.Illegal);
        assertBasicType(i.object(), CiKind.Object);
    }

    /**
     * Typechecks a BlockBegin instruction.
     * @param i the BlockBegin instruction to be verified
     */
    @Override
    public void visitBlockBegin(BlockBegin i) {
        BlockBegin b = idMap.get(i.blockID);
        if (b != null && b != i) {
            fail("Block id is not unique " + i + " and " + b);
        }
        idMap.put(i.blockID, i);
        assertNonNull(i.stateBefore(), "Block must have initial state");
        assertBasicType(i, CiKind.Illegal);
        if (i.depthFirstNumber() < -1) {
            fail("Block has an invalid depth first number");
        }
        assertNonNull(i.end(), "BlockBegin does not have BlockEnd");
        if (i.end().begin() != i) {
            fail("BlockEnd does not refer back to this BlockBegin");
        }
        // check that each predecessor has this block in its successor list
        List<BlockBegin> preds = i.predecessors();
        assertNonNull(preds, "Predecessor list does not exist");
        for (BlockBegin pred : preds) {
            List<BlockBegin> succ = i.isExceptionEntry() ? pred.exceptionHandlerBlocks() : pred.end().successors();
            if (!succ.contains(i)) {
                fail("Predecessor block does not have this block in its successor list");
            }
        }
        checkBlockEnd(i.end());
    }

    /**
     * Typechecks a Base instruction.
     * @param i the Base instruction to be verified
     */
    @Override
    public void visitBase(Base i) {
        assertBasicType(i, CiKind.Illegal);
        if (i.isSafepoint()) {
            fail("Value Base is not a safepoint instruction ");
        }
    }

    /**
     * Typechecks a Goto instruction.
     * @param i the Goto instruction to be verified
     */
    @Override
    public void visitGoto(Goto i) {
        assertBasicType(i, CiKind.Illegal);
        if (i.successors().size() != 1) {
            fail("Goto instruction must have one successor");
        }
        assertNonNull(i.stateAfter(), "Goto must have state after");
    }

    /**
     * Typechecks an If instruction.
     * @param i the If instruction to be verified
     */
    @Override
    public void visitIf(If i) {
        assertBasicType(i, CiKind.Illegal);
        if (!Util.equalKinds(i.x(), i.y())) {
            fail("Operands of If instruction must have same type");
        }
        if (i.successors().size() != 2) {
            fail("If instruction must have 2 successors");
        }
        assertNonNull(i.stateAfter(), "If must have state after");
    }

    void checkBlockEnd(BlockEnd i) {
        assertNonNull(i.begin(), "BlockEnd has no BlockBegin");
        // check that each successor has this block in its predecessor list
        List<BlockBegin> succs = i.successors();
        assertNonNull(succs, "BlockEnd successor list does not exist");
        for (BlockBegin succ : succs) {
            if (!succ.predecessors().contains(i.begin())) {
                fail("Successor block does not have this block in its predecessor list");
            }
        }
        if (i.next() != null) {
            fail("BlockEnd must not have next");
        }
    }

    /**
     * Typechecks an InstanceOf instruction.
     * @param i the InstanceOf instruction to be verified
     */
    @Override
    public void visitInstanceOf(InstanceOf i) {
        assertBasicType(i, CiKind.Int);
        assertBasicType(i.object(), CiKind.Object);
        assertNonNull(i.targetClass(), "targetClass in InsatanceOf instruction must be non null");
        assertNotPrimitive(i.targetClass());
    }

    /**
     * Typechecks a a Return instruction.
     * @param i the Return instruction to be verified
     */
    @Override
    public void visitReturn(Return i) {
        final Value result = i.result();

        CiKind retType = ir.compilation.method.signatureType().returnBasicType();
        if (result == null) {
            assertBasicType(i, CiKind.Void);
            if (retType != CiKind.Void) {
                fail("Must return value from non-void method");
            }
        } else {
            if (retType == CiKind.Void) {
                fail("Must not return value from void method");
            }
            if (i.type() == CiKind.Void) {
                fail("Return instruction must not be of type void if method returns a value");
            }
            assertBasicType(result, retType.stackType());
            if (i.type() != retType.stackType()) {
                fail("Return value type does not match the method's return type");
            }
        }
    }

    /**
     * Typechecks a LookupSwitch instruction.
     * @param i the LookupSwitch instruction to be verified
     */
    @Override
    public void visitLookupSwitch(LookupSwitch i) {
        assertBasicType(i, CiKind.Illegal);
        assertBasicType(i.value(), CiKind.Int);

        if (i.numberOfCases() > 1) {
            int min = i.keyAt(0);
            for (int j = 1; j < i.numberOfCases(); j++) {
                if (min >= i.keyAt(j)) {
                    fail("LookupSwitch keys must be ordered");
                }
                min = i.keyAt(j);
            }
        }
        if (i.numberOfCases() != i.keysLength()) {
            fail("Lookupswitch keys[] length does not match number of cases");
        }
    }

    /**
     * Typechecks a TableSwitch instruction.
     * @param i the TableSwitch instruction to be verified
     */
    @Override
    public void visitTableSwitch(TableSwitch i) {
        assertBasicType(i, CiKind.Illegal);
        assertBasicType(i.value(), CiKind.Int);
    }

    /**
     * Typechecks a Throw instruction.
     * @param i the Throw instruction to be verified
     */
    @Override
    public void visitThrow(Throw i) {
        assertBasicType(i, CiKind.Illegal);
        assertBasicType(i.exception(), CiKind.Object);
        assertInstanceType(i.exception().declaredType());
        assertInstanceType(i.exception().exactType());
    }

    /**
     * Typechecks an Instrinsic instruction.
     * @param i the Instrinsic instruction
     */
    @Override
    public void visitIntrinsic(Intrinsic i) {
        if (i.isIllegal()) {
            fail("Result type of Intrinsic instruction must not be Illegal");
        }
        // TODO: use the signature from the C1XIntrinsic to check arguments
    }

    /**
     * Typechecks an Invoke instruction.
     * @param i the Invoke instruction
     */
    @Override
    public void visitInvoke(Invoke i) {
        assertNonNull(i.target(), "Target of invoke cannot be null");
        assertNonNull(i.stateBefore(), "Invoke must have ValueStack");
        RiSignature signatureType = i.target().signatureType();
        assertBasicType(i, signatureType.returnBasicType().stackType());
        Value[] args = i.arguments();
        if (i.isStatic()) {
            // typecheck a static call (i.e. there should be no receiver)
            if (i.opcode() != Bytecodes.INVOKESTATIC) {
                fail("Static Invoke must use InvokeStatic bytecode");
            }
            int argSize = signatureType.argumentSlots(false);
            if (argSize != args.length) {
                fail("Size of Arguments does not match invoke signature");
            }
            typeCheckArguments(true, args, signatureType);
        } else {
            // typecheck a non-static call (i.e. there should be a receiver)
            assertNonNull(i.receiver(), "Receiver object should not be null");
            if (i.opcode() != Bytecodes.INVOKEVIRTUAL && i.opcode() != Bytecodes.INVOKESPECIAL && i.opcode() != Bytecodes.INVOKEINTERFACE) {
                fail("Nonstatic Invoke must use proper invoke bytecode");
            }
            typeCheckArguments(false, args, signatureType);
        }
    }

    private void typeCheckArguments(boolean isStatic, Value[] args, RiSignature signatureType) {
        int argSize = signatureType.argumentSlots(!isStatic);
        if (argSize != args.length) {
            fail("Size of arguments does not match invoke signature");
        }
        int k = 0; // loops over signature positions
        int j = 0; // loops over argument positions
        for (; j < argSize; j++) {
            if (!isStatic && j == 0) {
                assertBasicType(args[j], CiKind.Object);
            } else {
                CiKind basicType = signatureType.argumentBasicTypeAt(k);
                assertBasicType(args[j], basicType.stackType());
                if (basicType.sizeInSlots() == 2) {
                    assertNull(args[j + 1], "Second slot of a double operand must be null");
                    j = j + 1;
                }
                k = k + 1;
            }
        }
    }

    /**
     *  Typechecks the NewMultiArray instruction.
     *
     *  @param i the NewMultiArray instruction to check
     */
    @Override
    public void visitNewMultiArray(NewMultiArray i) {
        final Value[] dimensions = i.dimensions();
        assertBasicType(i, CiKind.Object);

        if (dimensions.length <= 1) {
            fail("Value NewMultiArray must have more than 1 dimension");
        }

        for (Value dim : dimensions) {
            assertBasicType(dim, CiKind.Int);
        }
    }

    /**
     * Typechecks the NewObjectArray instruction.
     * @param i the NewObjectArray instruction to be checked
     */
    @Override
    public void visitNewObjectArray(NewObjectArray i) {
        assertBasicType(i, CiKind.Object);
        assertBasicType(i.length(), CiKind.Int);
        assertNotPrimitive(i.elementClass());
    }

    /**
     *  Typechecks the NewTypeArray instruction.
     *  @param i the NewTypeArray instruction to check
     */
    @Override
    public void visitNewTypeArray(NewTypeArray i) {
        assertBasicType(i, CiKind.Object);
        assertBasicType(i.length(), CiKind.Int);
        assertPrimitive(i.elementType());
    }

    /**
     * Typechecks the NewTypeArray instruction.
     * @param i the NewInstance instruction to check
     */
    @Override
    public void visitNewInstance(NewInstance i) {
        assertBasicType(i, CiKind.Object);
        assertInstanceType(i.instanceClass());
    }

    /**
     * Typechecks the CheckCast instruction.
     * @param i the CheckCast instruction to check
     */
    @Override
    public void visitCheckCast(CheckCast i) {
        assertBasicType(i, CiKind.Object);
        assertBasicType(i.object(), CiKind.Object);
        if (i.targetClass().basicType() != CiKind.Object) {
            fail("Target class must be of type Object in a CheckCast instruction");
        }
    }

    /**
     * Typechecks the UnsafeGetObject instruction.
     * @param i the UnsafeGetObject instruction to check
     */
    @Override
    public void visitUnsafeGetObject(UnsafeGetObject i) {
        assertBasicType(i.object(), CiKind.Object);
    }

    /**
     * Typechecks the UnsafePrefetchRead instruction.
     * @param i the UnsafePrefetchRead instruction to check
     */
    @Override
    public void visitUnsafePrefetchRead(UnsafePrefetchRead i) {
        assertBasicType(i.object(), CiKind.Object);
    }

    /**
     * Typechecks the UnsafePrefetchWrite instruction.
     * @param i the UnsafePrefetchWrite instruction to check
     */
    @Override
    public void visitUnsafePrefetchWrite(UnsafePrefetchWrite i) {
        assertBasicType(i.object(), CiKind.Object);
    }

    /**
     * Typechecks the UnsafePutObject instruction.
     * @param i the UnsafePutObject instruction to check
     */
    @Override
    public void visitUnsafePutObject(UnsafePutObject i) {
        assertBasicType(i.object(), CiKind.Object);
        assertBasicType(i.offset(), CiKind.Int);
    }

    /**
     * Typechecks the UnsafeGetRaw instruction.
     * @param i the UnsafeGetRaw instruction to check
     */
    @Override
    public void visitUnsafeGetRaw(UnsafeGetRaw i) {
        if (i.base() != null) {
            assertBasicType(i.base(), CiKind.Long);
        }
        if (i.index() != null) {
            assertBasicType(i.index(), CiKind.Int);
        }
    }

    /**
     * Typechecks the UnsafePutRaw instruction.
     * @param i the UnsafePutRaw instruction to check
     */
    @Override
    public void visitUnsafePutRaw(UnsafePutRaw i) {
        if (i.base() != null) {
            assertBasicType(i.base(), CiKind.Long);
        }
        if (i.index() != null) {
            assertBasicType(i.index(), CiKind.Int);
        }
    }

    private void assertBasicType(Value i, CiKind basicType) {
        assertNonNull(i, "Value should not be null");
        if (i.type() != basicType) {
            fail("Type mismatch: " + i + " should be of type " + basicType);
        }
    }

    private void assertLegal(Value i) {
        assertNonNull(i, "Value should not be null");
        if (i.type() == CiKind.Illegal) {
            fail("Type mismatch: " + i + " should not be illegal");
        }
    }

    private void assertInstanceType(RiType riType) {
        if (riType != null && riType.isLoaded()) {
            if (riType.isArrayKlass() || riType.isInterface() || riType.basicType().isPrimitive()) {
                fail("RiType " + riType + " must be an instance class");
            }
        }
    }

    private void assertArrayType(RiType riType) {
        if (riType != null && riType.isLoaded()) {
            if (!riType.isArrayKlass()) {
                fail("RiType " + riType + " must be an array class");
            }
        }
    }

    private void assertNotPrimitive(RiType riType) {
        if (riType != null && riType.isLoaded()) {
            if (riType.basicType().isPrimitive()) {
                fail("RiType " + riType + " must not be a primitive");
            }
        }
    }

    private void assertPrimitive(CiKind basicType) {
        if (!basicType.isPrimitive()) {
            fail("RiType " + basicType + " must be a primitive");
        }
    }

    private void assertNonNull(Object object, String msg) {
        if (object == null) {
            fail(msg);
        }
    }

    private void assertNull(Object object, String msg) {
        if (object != null) {
            fail(msg);
        }
    }

    private void fail(String msg) {
        throw new IRCheckException(phase, msg);
    }

    private class BasicValueChecker implements ValueClosure {

        public Value apply(Value i) {
            if (i.subst() != i) {
                fail("instruction has unresolved substitution " + i + " -> " + i.subst());
            }
            if (!i.isDeadPhi()) {
                // legal instructions must have legal instructions as inputs
                LegalValueChecker legalChecker = new LegalValueChecker(i);
                i.inputValuesDo(legalChecker);
                if (i instanceof Phi) {
                    // phis are special, once again
                    checkPhi((Phi) i);
                }
            }
            if (i instanceof BlockEnd) {
                checkBlockEnd((BlockEnd) i);
            }
            return i;
        }
    }

    private class LegalValueChecker implements ValueClosure {
        private final Value i;

        public LegalValueChecker(Value i) {
            this.i = i;
        }

        public Value apply(Value x) {
            assertNonNull(x, "must have input value for " + i);
            if (x.isIllegal()) {
                fail("Value has illegal input value " + i + " <- " + x);
            }
            return x;
        }
    }
}
