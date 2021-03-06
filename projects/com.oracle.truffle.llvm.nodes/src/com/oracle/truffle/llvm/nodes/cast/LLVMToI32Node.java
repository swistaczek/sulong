/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64Node.LLVMToI64BitNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI32Node extends LLVMExpressionNode {

    @Specialization
    protected int doI32(VirtualFrame frame, LLVMFunctionDescriptor from,
                    @Cached("toNative()") LLVMToNativeNode toNative) {
        return (int) toNative.executeWithTarget(frame, from).getVal();
    }

    @Specialization
    protected int doGlobal(VirtualFrame frame, LLVMGlobal from,
                    @Cached("toNative()") LLVMToNativeNode access) {
        return (int) access.executeWithTarget(frame, from).getVal();
    }

    @Child private ForeignToLLVM convert = ForeignToLLVM.create(ForeignToLLVMType.I32);

    @Specialization
    protected int doLLVMTruffleObject(VirtualFrame frame, LLVMTruffleObject from,
                    @Cached("toNative()") LLVMToNativeNode toNative) {
        return (int) toNative.executeWithTarget(frame, from).getVal();
    }

    @Specialization
    protected int doLLVMBoxedPrimitive(VirtualFrame frame, LLVMBoxedPrimitive from) {
        return (int) convert.executeWithTarget(frame, from.getValue());
    }

    public abstract static class LLVMToI32NoZeroExtNode extends LLVMToI32Node {

        @Specialization
        protected int doI32(boolean from) {
            return from ? -1 : 0;
        }

        @Specialization
        protected int doI32(byte from) {
            return from;
        }

        @Specialization
        protected int doI32(short from) {
            return from;
        }

        @Specialization
        protected int doI32(LLVMAddress from) {
            return (int) from.getVal();
        }

        @Specialization
        protected int doI32(long from) {
            return (int) from;
        }

        @Specialization
        protected int doI32(LLVMIVarBit from) {
            return from.getIntValue();
        }

        @Specialization
        protected int doI32(float from) {
            return (int) from;
        }

        @Specialization
        protected int doI32(double from) {
            return (int) from;
        }

        @Specialization
        protected int doI32(LLVM80BitFloat from) {
            return from.getIntValue();
        }

        @Specialization
        protected int doI32(int from) {
            return from;
        }
    }

    public abstract static class LLVMToI32ZeroExtNode extends LLVMToI32Node {

        @Specialization
        protected int doI32(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        protected int doI32(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        protected int doI32(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        protected int doI32(LLVMIVarBit from) {
            return from.getZeroExtendedIntValue();
        }

        @Specialization
        protected int doI32(int from) {
            return from;
        }
    }

    public abstract static class LLVMToI32BitNode extends LLVMToI32Node {

        @Specialization
        protected int doI32(float from) {
            return Float.floatToIntBits(from);
        }

        @Specialization
        protected int doI32(int from) {
            return from;
        }

        @Specialization
        protected int doI1Vector(LLVMI1Vector from) {
            return (int) LLVMToI64BitNode.castI1Vector(from, Integer.SIZE);
        }

        @Specialization
        protected int doI8Vector(LLVMI8Vector from) {
            return (int) LLVMToI64BitNode.castI8Vector(from, Integer.SIZE / Byte.SIZE);
        }

        @Specialization
        protected int doI16Vector(LLVMI16Vector from) {
            return (int) LLVMToI64BitNode.castI16Vector(from, Integer.SIZE / Short.SIZE);
        }

        @Specialization
        protected int doI32Vector(LLVMI32Vector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return from.getValue(0);
        }

        @Specialization
        protected int doFloatVector(LLVMFloatVector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return Float.floatToIntBits(from.getValue(0));
        }
    }

    public abstract static class LLVMToUnsignedI32Node extends LLVMToI32Node {

        @Specialization
        protected int doI32(double from) {
            if (from > Integer.MAX_VALUE) {
                return (int) (from + Integer.MIN_VALUE) - Integer.MIN_VALUE;
            }
            return (int) from;
        }

        @Specialization
        protected int doI32(int from) {
            return from;
        }
    }
}
