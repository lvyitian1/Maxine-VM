/*
 * Copyright (c) 2017, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.target.aarch64;

import com.oracle.max.asm.NumUtil;
import com.oracle.max.asm.target.aarch64.Aarch64;
import com.sun.c1x.C1XCompilation;
import com.sun.c1x.alloc.OperandPool.VariableFlag;
import com.sun.c1x.gen.LIRGenerator;
import com.sun.c1x.gen.LIRItem;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.LIRDebugInfo;
import com.sun.c1x.lir.LIROpcode;
import com.sun.c1x.stub.CompilerStub;
import com.sun.c1x.util.Util;
import com.sun.cri.bytecode.Bytecodes;
import com.sun.cri.ci.*;

import static com.sun.cri.bytecode.Bytecodes.*;

public class Aarch64LIRGenerator extends LIRGenerator {

    // TODO: (ck) We have to change those to ARM register terminology
    private static final CiRegisterValue RAX_I = Aarch64.r0.asValue(CiKind.Int);
    private static final CiRegisterValue RAX_L = Aarch64.r0.asValue(CiKind.Long);
    private static final CiRegisterValue RDX_I = Aarch64.r1.asValue(CiKind.Int);
    private static final CiRegisterValue RDX_L = Aarch64.r1.asValue(CiKind.Long);
    private static final CiRegisterValue RETURNREG_L = Aarch64.r0.asValue(CiKind.Long);
    private static final CiRegisterValue RETURNREG_I = Aarch64.r0.asValue(CiKind.Int);

    private static final CiRegisterValue LDIV_TMP = RDX_L;

    private static final CiRegisterValue LMUL_OUT = RAX_L;

    private static final CiRegisterValue SHIFT_COUNT_IN = Aarch64.r1.asValue(CiKind.Int);

    protected static final CiValue ILLEGAL = CiValue.IllegalValue;

    public Aarch64LIRGenerator(C1XCompilation compilation) {
        super(compilation);
    }

    @Override
    protected CiValue exceptionPcOpr() {
        return ILLEGAL;
    }

    @Override
    protected boolean canStoreAsConstant(Value v, CiKind kind) {
        if (kind == CiKind.Short || kind == CiKind.Char) {
            // there is no immediate move of word values in asemblerI486.?pp
            return false;
        }
        return v instanceof Constant;
    }

    @Override
    protected boolean canInlineAsConstant(Value v) {
        if (v.kind == CiKind.Long) {
            if (v.isConstant() && NumUtil.isInt(v.asConstant().asLong())) {
                return true;
            }
            return false;
        }
        return v.kind != CiKind.Object || v.isNullConstant();
    }

    @Override
    protected CiAddress genAddress(CiValue base, CiValue index, int shift, int disp, CiKind kind) {
        assert base.isVariableOrRegister();
        if (index.isConstant()) {
            return new CiAddress(kind, base, (((CiConstant) index).asInt() << shift) + disp);
        } else {
            assert index.isVariableOrRegister();
            return new CiAddress(kind, base, index, CiAddress.Scale.fromShift(shift), disp);
        }
    }

    @Override
    protected void genCmpMemInt(Condition condition, CiValue base, int disp, int c, LIRDebugInfo info) {
        lir.cmpMemInt(condition, base, disp, c, info);
    }

    @Override
    protected void genCmpRegMem(Condition condition, CiValue reg, CiValue base, int disp, CiKind kind, LIRDebugInfo info) {
        lir.cmpRegMem(condition, reg, new CiAddress(kind, base, disp), info);
    }

    @Override
    protected boolean strengthReduceMultiply(CiValue left, int c, CiValue result, CiValue tmp) {
        if (tmp.isLegal()) {
            if (CiUtil.isPowerOf2(c + 1)) {
                lir.move(left, tmp);
                lir.shiftLeft(left, CiUtil.log2(c + 1), left);
                lir.sub(left, tmp, result);
                return true;
            } else if (CiUtil.isPowerOf2(c - 1)) {
                lir.move(left, tmp);
                lir.shiftLeft(left, CiUtil.log2(c - 1), left);
                lir.add(left, tmp, result);
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitNegateOp(NegateOp x) {
        LIRItem value = new LIRItem(x.x(), this);
        value.setDestroysRegister();
        value.loadItem();
        CiVariable reg = newVariable(x.kind);
        lir.negate(value.result(), reg, null);
        setResult(x, reg);
    }

    @Override
    public void visitSignificantBit(SignificantBitOp x) {
        LIRItem value = new LIRItem(x.value(), this);
        value.setDestroysRegister();
        value.loadItem();
        CiValue reg = createResultVariable(x);
        if (x.op == LIROpcode.Lsb) {
            lir.lsb(value.result(), reg);
        } else {
            lir.msb(value.result(), reg);
        }
    }

    public boolean livesLonger(Value x, Value y) {
        BlockBegin bx = x.block();
        BlockBegin by = y.block();
        if (bx == null || by == null) {
            return false;
        }
        return bx.loopDepth() < by.loopDepth();
    }

    public void visitArithmeticOpFloat(ArithmeticOp x) {
        LIRItem left = new LIRItem(x.x(), this);
        LIRItem right = new LIRItem(x.y(), this);
        assert !left.isStack() || !right.isStack() : "can't both be memory operands";
        boolean mustLoadBoth = x.opcode == Bytecodes.FREM || x.opcode == Bytecodes.DREM;

        // Both are in register, swap operands such that the short-living one is on the left side.
        if (x.isCommutative() && left.isRegisterOrVariable() && right.isRegisterOrVariable()) {
            if (livesLonger(x.x(), x.y())) {
                LIRItem tmp = left;
                left = right;
                right = tmp;
            }
        }

        if (left.isRegisterOrVariable() || x.x().isConstant() || mustLoadBoth) {
            left.loadItem();
        }

        if (mustLoadBoth) {
            // frem and drem destroy also right operand, so move it to a new register
            right.setDestroysRegister();
            right.loadItem();
        } else if (right.isRegisterOrVariable()) {
            right.loadItem();
        }

        CiVariable reg = newVariable(x.kind);
        arithmeticOpFpu(x.opcode, reg, left.result(), right.result(), ILLEGAL);

        setResult(x, reg);
    }

    protected void arithmeticOpFpu(int code, CiValue result, CiValue left, CiValue right, CiValue tmp) {
        CiValue leftOp = left;

        if (isTwoOperand && leftOp != result) {
            assert right != result : "malformed";
            lir.move(leftOp, result);
            leftOp = result;
        }

        switch (code) {
            case DADD:
            case FADD:
                lir.add(leftOp, right, result);
                break;
            case FMUL:
            case DMUL:
                lir.mul(leftOp, right, result);
                break;
            case DSUB:
            case FSUB:
                lir.sub(leftOp, right, result);
                break;
            case FDIV:
            case DDIV:
                lir.div(leftOp, right, result, null);
                break;
            case FREM:
            case DREM:
                lir.rem(leftOp, right, result, null);
                break;
            default:
                Util.shouldNotReachHere();
        }
    }

    public void visitArithmeticOpLong(ArithmeticOp x) {
        int opcode = x.opcode;
        if (opcode == Bytecodes.LDIV || opcode == Bytecodes.LREM || opcode == Op2.UDIV || opcode == Op2.UREM) {
            LIRDebugInfo info = x.needsZeroCheck() ? stateFor(x) : null;
            CiValue dividend = force(x.x(), RAX_L);
            CiValue divisor = load(x.y());

            CiValue result = createResultVariable(x);
            switch (opcode) {
                case Bytecodes.LREM:
                    lir.lrem(dividend, divisor, RETURNREG_L, LDIV_TMP, info);
                    break;
                case Bytecodes.LDIV:
                    lir.ldiv(dividend, divisor, RETURNREG_L, LDIV_TMP, info);
                    break;
                case Op2.UREM:
                    lir.lurem(dividend, divisor, RETURNREG_L, LDIV_TMP, info);
                    break;
                case Op2.UDIV:
                    lir.ludiv(dividend, divisor, RETURNREG_L, LDIV_TMP, info);
                    break;
                default:
                    throw Util.shouldNotReachHere();
            }
            lir.move(RETURNREG_L, result);
        } else if (opcode == Bytecodes.LMUL) {
            LIRItem right = new LIRItem(x.y(), this);

            // right register is destroyed by the long mul, so it must be
            // copied to a new register.
            right.setDestroysRegister();

            CiValue left = load(x.x());
            right.loadItem();

            arithmeticOpLong(opcode, LMUL_OUT, left, right.result(), null);
            CiValue result = createResultVariable(x);
            lir.move(LMUL_OUT, result);
        } else {
            LIRItem right = new LIRItem(x.y(), this);

            CiValue left = load(x.x());
            // don't load constants to save register
            right.loadNonconstant();
            createResultVariable(x);
            arithmeticOpLong(opcode, x.operand(), left, right.result(), null);
        }
    }

    public void visitArithmeticOpInt(ArithmeticOp x) {
        int opcode = x.opcode;
        if (opcode == Bytecodes.IDIV || opcode == Bytecodes.IREM || opcode == Op2.UDIV || opcode == Op2.UREM) {
            // emit code for integer division or modulus

            // Call 'stateFor' before 'force()' because 'stateFor()' may
            // force the evaluation of other instructions that are needed for
            // correct debug info. Otherwise the live range of the fixed
            // register might be too long.
            LIRDebugInfo info = x.needsZeroCheck() ? stateFor(x) : null;

            CiValue dividend = force(x.x(), RAX_I); // dividend must be in RAX
            CiValue divisor = load(x.y()); // divisor can be in any (other) register

            // idiv and irem use rdx in their implementation so the
            // register allocator must not assign it to an interval that overlaps
            // this division instruction.
            CiRegisterValue tmp = RDX_I;

            CiValue result = createResultVariable(x);
            CiValue resultReg;
            if (opcode == Bytecodes.IREM) {
                resultReg = tmp; // remainder result is produced in rdx
                lir.irem(dividend, divisor, resultReg, tmp, info);
            } else if (opcode == Bytecodes.IDIV) {
                resultReg = RAX_I; // division result is produced in rax
                lir.idiv(dividend, divisor, resultReg, tmp, info);
            } else if (opcode == Op2.UREM) {
                resultReg = tmp; // remainder result is produced in rdx
                lir.iurem(dividend, divisor, resultReg, tmp, info);
            } else if (opcode == Op2.UDIV) {
                resultReg = RAX_I; // division result is produced in rax
                lir.iudiv(dividend, divisor, resultReg, tmp, info);
            } else {
                throw Util.shouldNotReachHere();
            }

            lir.move(resultReg, result);
        } else {
            // emit code for other integer operations
            LIRItem left = new LIRItem(x.x(), this);
            LIRItem right = new LIRItem(x.y(), this);
            LIRItem leftArg = left;
            LIRItem rightArg = right;
            if (x.isCommutative() && left.isStack() && right.isRegisterOrVariable()) {
                // swap them if left is real stack (or cached) and right is real register(not cached)
                leftArg = right;
                rightArg = left;
            }

            leftArg.loadItem();

            // do not need to load right, as we can handle stack and constants
            if (opcode == Bytecodes.IMUL) {
                // check if we can use shift instead
                boolean useConstant = false;
                boolean useTmp = false;
                if (rightArg.result().isConstant()) {
                    int iconst = rightArg.instruction.asConstant().asInt();
                    if (iconst > 0) {
                        if (CiUtil.isPowerOf2(iconst)) {
                            useConstant = true;
                        } else if (CiUtil.isPowerOf2(iconst - 1) || CiUtil.isPowerOf2(iconst + 1)) {
                            useConstant = true;
                            useTmp = true;
                        }
                    }
                }

                if (!useConstant) {
                    rightArg.loadItem();
                }
                CiValue tmp = ILLEGAL;
                if (useTmp) {
                    tmp = newVariable(CiKind.Int);
                }
                createResultVariable(x);
                arithmeticOpInt(opcode, x.operand(), leftArg.result(), rightArg.result(), tmp);
            } else {
                createResultVariable(x);
                CiValue tmp = ILLEGAL;
                arithmeticOpInt(opcode, x.operand(), leftArg.result(), rightArg.result(), tmp);
            }
        }
    }

    @Override
    public void visitArithmeticOp(ArithmeticOp x) {
        trySwap(x);
        assert Util.archKindsEqual(x.x().kind, x.kind) && Util.archKindsEqual(x.y().kind, x.kind) : "wrong parameter types: " + Bytecodes.nameOf(x.opcode);
        switch (x.kind) {
            case Float:
            case Double:
                visitArithmeticOpFloat(x);
                return;
            case Long:
                visitArithmeticOpLong(x);
                return;
            case Int:
                visitArithmeticOpInt(x);
                return;
        }
        throw Util.shouldNotReachHere();
    }

    @Override
    public void visitShiftOp(ShiftOp x) {
        // count must always be in rcx
        CiValue count = makeOperand(x.y());
        boolean mustLoadCount = !count.isConstant() || x.kind == CiKind.Long;
        if (mustLoadCount) {
            // count for long must be in register
            count = force(x.y(), SHIFT_COUNT_IN);
        }

        CiValue value = load(x.x());
        CiValue reg = createResultVariable(x);
        shiftOp(x.opcode, reg, value, count, ILLEGAL);
    }

    @Override
    public void visitLogicOp(LogicOp x) {
        trySwap(x);

        LIRItem right = new LIRItem(x.y(), this);

        CiValue left = load(x.x());
        right.loadNonconstant();
        CiValue reg = createResultVariable(x);
        logicOp(x.opcode, reg, left, right.result());
    }

    private void trySwap(Op2 x) {
        // (tw) TODO: Check what this is for?
    }

    @Override
    public void visitCompareOp(CompareOp x) {
        LIRItem left = new LIRItem(x.x(), this);
        LIRItem right = new LIRItem(x.y(), this);
        if (!x.kind.isVoid() && x.x().kind.isLong()) {
            left.setDestroysRegister();
        }
        left.loadItem();
        right.loadItem();

        if (x.kind.isVoid()) {
            lir.cmp(Condition.TRUE, left.result(), right.result());
        } else if (x.x().kind.isFloat() || x.x().kind.isDouble()) {
            CiValue reg = createResultVariable(x);
            int code = x.opcode;
            lir.fcmp2int(left.result(), right.result(), reg, code == Bytecodes.FCMPL || code == Bytecodes.DCMPL);
        } else if (x.x().kind.isLong()) {
            CiValue reg = createResultVariable(x);
            lir.lcmp2int(left.result(), right.result(), reg);
        } else {
            Util.unimplemented();
        }
    }

    @Override
    public void visitUnsignedCompareOp(UnsignedCompareOp x) {
        LIRItem left = new LIRItem(x.x(), this);
        LIRItem right = new LIRItem(x.y(), this);
        left.loadItem();
        right.loadItem();
        CiValue result = createResultVariable(x);
        lir.cmp(x.condition, left.result(), right.result());
        lir.cmove(x.condition, CiConstant.INT_1, CiConstant.INT_0, result);
    }

    @Override
    public void visitCompareAndSwap(CompareAndSwap x) {

        // (tw) TODO: Factor out common code with genCompareAndSwap.

        CiKind dataKind = x.dataType.kind(true);
        CiValue tempPointer = load(x.pointer());
        CiAddress addr = getAddressForPointerOp(x, dataKind, tempPointer);

        CiValue expectedValue = load(x.expectedValue());
        CiValue newValue = load(x.newValue());
        assert Util.archKindsEqual(newValue.kind, dataKind) : "invalid type";

        if (dataKind.isObject()) { // Write-barrier needed for Object fields.
            // Do the pre-write barrier : if any.
            preGCWriteBarrier(addr, false, null);
        }

        CiValue pointer = newVariable(compilation.target.wordKind);
        lir.lea(addr, pointer);
        CiValue result = createResultVariable(x);
        CiValue resultReg = compilation.registerConfig.getScratchRegister().asValue(dataKind);
        if (dataKind.isObject()) {
            lir.casObj(pointer, expectedValue, newValue);
        } else if (dataKind.isInt()) {
            lir.casInt(pointer, expectedValue, newValue);
        } else {
            assert dataKind.isLong();
            lir.casLong(pointer, expectedValue, newValue);
        }

        lir.move(resultReg, result);

        if (dataKind.isObject()) { // Write-barrier needed for Object fields.
            // Seems to be precise
            postGCWriteBarrier(pointer, newValue);
        }
    }

    @Override
    protected void genCompareAndSwap(Intrinsic x, CiKind kind) {
        assert x.numberOfArguments() == 5 : "wrong number of arguments: " + x.numberOfArguments();
        // Argument 0 is the receiver.
        LIRItem obj = new LIRItem(x.argumentAt(1), this);
        LIRItem offset = new LIRItem(x.argumentAt(2), this);
        LIRItem val = new LIRItem(x.argumentAt(4), this);

        assert obj.instruction.kind.isObject() : "invalid type";
        assert val.instruction.kind == kind : "invalid type";

        // get address of field
        obj.loadItem();
        offset.loadNonconstant();
        CiAddress addr;
        if (offset.result().isConstant()) {
            addr = new CiAddress(kind, obj.result(), ((CiConstant) offset.result()).asInt());
        } else {
            addr = new CiAddress(kind, obj.result(), offset.result());
        }

        // Compare operand needs to be in ScratchRegister
        CiValue cmp = force(x.argumentAt(3), compilation.registerConfig.getScratchRegister().asValue(kind));
        val.loadItem();

        CiValue pointer = newVariable(compilation.target.wordKind);
        lir.lea(addr, pointer);

        if (kind.isObject()) { // Write-barrier needed for Object fields.
            // Do the pre-write barrier : if any.
            preGCWriteBarrier(pointer, false, null);
        }

        if (kind.isObject()) {
            lir.casObj(pointer, cmp, val.result());
        } else if (kind.isInt()) {
            lir.casInt(pointer, cmp, val.result());
        } else if (kind.isLong()) {
            lir.casLong(pointer, cmp, val.result());
        } else {
            Util.shouldNotReachHere();
        }

        // generate conditional move of boolean result
        CiValue result = createResultVariable(x);
        lir.cmove(Condition.EQ, CiConstant.INT_1, CiConstant.INT_0, result);
        if (kind.isObject()) { // Write-barrier needed for Object fields.
            // Seems to be precise
            postGCWriteBarrier(pointer, val.result());
        }
    }

    @Override
    protected void genMathIntrinsic(Intrinsic x) {
        assert x.numberOfArguments() == 1 : "wrong type";

        CiValue calcInput = load(x.argumentAt(0));

        switch (x.intrinsic()) {
            case java_lang_Math$abs:
                lir.abs(calcInput, createResultVariable(x), ILLEGAL);
                break;
            case java_lang_Math$sqrt:
                lir.sqrt(calcInput, createResultVariable(x), ILLEGAL);
                break;
            case java_lang_Math$sin:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticSin, null, calcInput));
                break;
            case java_lang_Math$cos:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticCos, null, calcInput));
                break;
            case java_lang_Math$tan:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticTan, null, calcInput));
                break;
            case java_lang_Math$log:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticLog, null, calcInput));
                break;
            case java_lang_Math$log10:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticLog10, null, calcInput));
                break;
            default:
                Util.shouldNotReachHere("Unknown math intrinsic");
        }
    }

    @Override
    public void visitConvert(Convert x) {
        CiValue input = load(x.value());
        CiVariable result = newVariable(x.kind);
        CompilerStub stub = null;

        // Checkstyle: off
        switch (x.opcode) {
            case F2I:
                stub = stubFor(CompilerStub.Id.f2i);
                break;
            case F2L:
                stub = stubFor(CompilerStub.Id.f2l);
                break;
            case D2I:
                stub = stubFor(CompilerStub.Id.d2i);
                break;
            case D2L:
                stub = stubFor(CompilerStub.Id.d2l);
                break;
        }
        // Checkstyle: on

        if (stub != null) {
            // Force result to be rax to match compiler stubs expectation.
            CiValue stubResult = x.kind == CiKind.Int ? RAX_I : RAX_L;
            lir.convert(x.opcode, input, stubResult, stub);
            lir.move(stubResult, result);
        } else {
            lir.convert(x.opcode, input, result, stub);
        }
        setResult(x, result);
    }

    @Override
    public void visitBlockBegin(BlockBegin x) {
        // nothing to do for now
    }

    @Override
    public void visitIf(If x) {
        CiKind kind = x.x().kind;

        Condition cond = x.condition();

        LIRItem xitem = new LIRItem(x.x(), this);
        LIRItem yitem = new LIRItem(x.y(), this);
        LIRItem xin = xitem;
        LIRItem yin = yitem;

        if (kind.isLong()) {
            // for longs, only conditions "eql", "neq", "lss", "geq" are valid;
            // mirror for other conditions
            if (cond == Condition.GT || cond == Condition.LE) {
                cond = cond.mirror();
                xin = yitem;
                yin = xitem;
            }
            xin.setDestroysRegister();
        }
        xin.loadItem();
        if (kind.isLong() && yin.result().isConstant() && yin.instruction.asConstant().asLong() == 0 && (cond == Condition.EQ || cond == Condition.NE)) {
            // dont load item
        } else if (kind.isLong() || kind.isFloat() || kind.isDouble()) {
            // longs cannot handle constants at right side
            yin.loadItem();
        }

        // add safepoint before generating condition code so it can be recomputed
        if (x.isSafepointPoll()) {
            emitXir(xir.genSafepointPoll(site(x)), x, stateFor(x, x.stateAfter()), null, false);
        }
        setNoResult(x);

        CiValue left = xin.result();
        CiValue right = yin.result();
        lir.cmp(cond, left, right);
        moveToPhi(x.stateAfter());
        if (x.x().kind.isFloat() || x.x().kind.isDouble()) {
            lir.branch(cond, right.kind, x.trueSuccessor(), x.unorderedSuccessor());
        } else {
            lir.branch(cond, right.kind, x.trueSuccessor());
        }
        assert x.defaultSuccessor() == x.falseSuccessor() : "wrong destination above";
        lir.jump(x.defaultSuccessor());
    }

    @Override
    public void visitIfBit(IfBit i) {
        CiAddress address = new CiAddress(CiKind.Byte, i.register.asValue(i.kind), i.offset);
        lir.testbit(address, CiConstant.forInt(i.bitNo));
        lir.branch(i.condition == Condition.EQ ? Condition.AE : Condition.BT, CiKind.Int, i.trueSuccessor());
        lir.jump(i.falseSuccessor());
    }

    @Override
    protected void genGetObjectUnsafe(CiValue dst, CiValue src, CiValue offset, CiKind kind, boolean isVolatile) {
        if (isVolatile && kind == CiKind.Long) {
            CiAddress addr = new CiAddress(CiKind.Double, src, offset);
            CiValue tmp = newVariable(CiKind.Double);
            lir.load(addr, tmp, null);
            CiValue spill = operands.newVariable(CiKind.Long, VariableFlag.MustStartInMemory);
            lir.move(tmp, spill);
            lir.move(spill, dst);
        } else {
            CiAddress addr = new CiAddress(kind, src, offset);
            lir.load(addr, dst, null);
        }
    }

    @Override
    protected void genPutObjectUnsafe(CiValue src, CiValue offset, CiValue data, CiKind kind, boolean isVolatile) {
        if (isVolatile && kind == CiKind.Long) {
            CiAddress addr = new CiAddress(CiKind.Double, src, offset);
            CiValue tmp = newVariable(CiKind.Double);
            CiValue spill = operands.newVariable(CiKind.Double, VariableFlag.MustStartInMemory);
            lir.move(data, spill);
            lir.move(spill, tmp);
            lir.move(tmp, addr);
        } else {
            CiAddress addr = new CiAddress(kind, src, offset);
            boolean isObj = kind == CiKind.Jsr || kind == CiKind.Object;
            if (isObj) {
                // Do the pre-write barrier, if any.
                preGCWriteBarrier(addr, false, null);
                lir.move(data, addr);
                assert src.isVariableOrRegister() : "must be register";
                // Seems to be a precise address
                postGCWriteBarrier(addr, data);
            } else {
                lir.move(data, addr);
            }
        }
    }

    @Override
    protected CiValue osrBufferPointer() {
        return Util.nonFatalUnimplemented(null);
    }

    @Override
    public void visitBoundsCheck(BoundsCheck boundsCheck) {
        Value x = boundsCheck.index();
        Value y = boundsCheck.length();
        CiValue left = load(x);
        CiValue right = null;
        if (y.isConstant()) {
            right = makeOperand(y);
        } else {
            right = load(y);
        }
        lir.cmp(boundsCheck.condition.negate(), left, right);
        emitGuard(boundsCheck);
    }
}
