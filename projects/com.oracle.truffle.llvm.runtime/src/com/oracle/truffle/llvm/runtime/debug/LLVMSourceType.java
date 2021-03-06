/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.function.Supplier;

public abstract class LLVMSourceType {

    public static final LLVMSourceType UNKNOWN_TYPE = new LLVMSourceType(() -> "<unknown>", 0, 0, 0, null) {

        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    };

    public static final LLVMSourceType VOID_TYPE = new LLVMSourceType(() -> "void", 0, 0, 0, null) {
        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    };

    private final LLVMSourceLocation location;
    private final long size;
    private final long align;
    private final long offset;
    @CompilationFinal private Supplier<String> nameSupplier;

    public LLVMSourceType(Supplier<String> nameSupplier, long size, long align, long offset, LLVMSourceLocation location) {
        this.nameSupplier = nameSupplier;
        this.size = size;
        this.align = align;
        this.offset = offset;
        this.location = location;
    }

    LLVMSourceType(long size, long align, long offset, LLVMSourceLocation location) {
        this(UNKNOWN_TYPE::getName, size, align, offset, location);
    }

    @TruffleBoundary
    public String getName() {
        return nameSupplier.get();
    }

    public void setName(Supplier<String> nameSupplier) {
        CompilerAsserts.neverPartOfCompilation();
        this.nameSupplier = nameSupplier;
    }

    public long getSize() {
        return size;
    }

    public long getAlign() {
        return align;
    }

    public long getOffset() {
        return offset;
    }

    public abstract LLVMSourceType getOffset(long newOffset);

    public boolean isPointer() {
        return false;
    }

    public boolean isAggregate() {
        return false;
    }

    public boolean isEnum() {
        return false;
    }

    public int getElementCount() {
        return 0;
    }

    public String getElementName(@SuppressWarnings("unused") long i) {
        return null;
    }

    public LLVMSourceType getElementType(@SuppressWarnings("unused") long i) {
        return null;
    }

    public LLVMSourceType getElementType(@SuppressWarnings("unused") String name) {
        return null;
    }

    public LLVMSourceLocation getElementDeclaration(@SuppressWarnings("unused") long i) {
        return null;
    }

    public LLVMSourceLocation getElementDeclaration(@SuppressWarnings("unused") String name) {
        return null;
    }

    public LLVMSourceLocation getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return getName();
    }
}
