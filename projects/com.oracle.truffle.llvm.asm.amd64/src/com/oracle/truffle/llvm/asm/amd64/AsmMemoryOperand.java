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
package com.oracle.truffle.llvm.asm.amd64;

public class AsmMemoryOperand extends AsmOperand {
    private final String displacement;
    private final AsmRegisterOperand base;
    private final AsmRegisterOperand offset;
    private final String scale;

    public AsmMemoryOperand(String displacement) {
        this(displacement, null, null, null);
    }

    public AsmMemoryOperand(String displacement, AsmRegisterOperand base) {
        this(displacement, base, null, null);
    }

    public AsmMemoryOperand(String displacement, AsmRegisterOperand base, AsmRegisterOperand offset) {
        this(displacement, base, offset, null);
    }

    public AsmMemoryOperand(String displacement, AsmRegisterOperand base, AsmRegisterOperand offset, String scale) {
        this.displacement = displacement;
        this.base = base;
        this.offset = offset;
        this.scale = scale;
    }

    public String getDisplacement() {
        return displacement;
    }

    public AsmRegisterOperand getBase() {
        return base;
    }

    public AsmRegisterOperand getOffset() {
        return offset;
    }

    public String getScale() {
        return scale;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(displacement);
        if (base != null || offset != null) {
            b.append("(");
            if (base != null) {
                b.append(base);
            }
            if (offset != null) {
                b.append(",");
                b.append(offset);
                if (scale != null) {
                    b.append(",");
                    b.append(scale);
                }
            }
            b.append(")");
        }
        return b.toString();
    }
}
