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
package com.oracle.truffle.llvm;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.BitcodeParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.parser.scanner.LLVMScanner;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.nfi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.types.Parser;

public final class Runner {

    private final NodeFactory nodeFactory;

    static final class SulongLibrary implements TruffleObject {

        private final LLVMContext context;
        private final String libraryName;

        private SulongLibrary(LLVMContext context, String libraryName) {
            this.context = context;
            this.libraryName = libraryName;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return SulongLibraryMessageResolutionForeign.ACCESS;
        }
    }

    @MessageResolution(receiverType = SulongLibrary.class)
    abstract static class SulongLibraryMessageResolution {

        @Resolve(message = "IS_NULL")
        abstract static class IsNullNode extends Node {

            Object access(SulongLibrary boxed) {
                return boxed.context == null;
            }
        }

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {

            @TruffleBoundary
            Object access(SulongLibrary boxed, String name) {
                String atname = "@" + name;
                LLVMFunctionDescriptor d = lookup(boxed, atname);
                if (d != null) {
                    return d;
                }
                return lookup(boxed, name);
            }
        }

        private static LLVMFunctionDescriptor lookup(SulongLibrary boxed, String name) {
            LLVMContext context = boxed.context;
            if (context.getGlobalScope().functionExists(name)) {
                LLVMFunctionDescriptor d = context.getGlobalScope().getFunctionDescriptor(name);
                if (d.getLibraryName().equals(boxed.libraryName)) {
                    return d;
                }
            }
            return null;
        }

        @CanResolve
        abstract static class CanResolveNoMain extends Node {

            boolean test(TruffleObject object) {
                return object instanceof SulongLibrary;
            }
        }
    }

    public Runner(NodeFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    public CallTarget parse(LLVMLanguage language, LLVMContext context, Source code) throws IOException {
        try {

            CallTarget mainFunction = null;
            ByteBuffer bytes;
            String libraryName = null;

            if (code.getMimeType().equals(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE)) {
                ByteBuffer buffer = Charset.forName("ascii").newEncoder().encode(CharBuffer.wrap(code.getCharacters()));
                bytes = Base64.getDecoder().decode(buffer);
                libraryName = "<STREAM>";
            } else if (code.getMimeType().equals(LLVMLanguage.LLVM_SULONG_TYPE)) {
                NativeLibraryDescriptor descriptor = Parser.parseLibraryDescriptor(code.getCharacters());
                String filename = descriptor.getFilename();
                libraryName = filename;
                bytes = read(filename);
            } else if (code.getPath() != null) {
                libraryName = code.getPath();
                bytes = read(code.getPath());
            } else {
                throw new IllegalStateException();
            }

            assert libraryName != null;
            assert bytes != null;
            if (!LLVMScanner.isSupportedFile(bytes)) {
                throw new IOException("Unsupported file: " + code.toString());
            }

            BitcodeParserResult bitcodeParserResult = BitcodeParserResult.getFromSource(code, bytes);
            context.addLibraryPaths(bitcodeParserResult.getLibraryPaths());
            context.addExternalLibraries(bitcodeParserResult.getLibraries());
            parseDynamicBitcodeLibraries(language, context);
            LLVMParserResult parserResult = parseBitcodeFile(code, libraryName, bitcodeParserResult, language, context);
            mainFunction = parserResult.getMainCallTarget();
            if (context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
                mainFunction = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            } else if (mainFunction == null) {
                mainFunction = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new SulongLibrary(context, libraryName)));
            }
            handleParserResult(context, parserResult);
            return mainFunction;
        } catch (Throwable t) {
            throw new IOException("Error while trying to parse " + code.getPath(), t);
        }
    }

    private static ByteBuffer read(String filename) {
        try {
            return ByteBuffer.wrap(Files.readAllBytes(Paths.get(filename)));
        } catch (IOException ignore) {
            return ByteBuffer.allocate(0);
        }
    }

    private static void visitBitcodeLibraries(LLVMContext context, Consumer<Source> sharedLibraryConsumer) throws IOException {
        List<Path> externalLibraries = context.getExternalLibraries(p -> p.toString().endsWith(".bc"));
        for (Path p : externalLibraries) {
            addLibrary(p, sharedLibraryConsumer);
        }
    }

    private static void addLibrary(Path s, Consumer<Source> sharedLibraryConsumer) throws IOException {
        File lib = s.toFile();
        Source source = Source.newBuilder(lib).build();
        sharedLibraryConsumer.accept(source);
    }

    private void parseDynamicBitcodeLibraries(LLVMLanguage language, LLVMContext context) throws IOException {
        if (!context.bcLibrariesLoaded()) {
            context.setBcLibrariesLoaded();
            visitBitcodeLibraries(context, source -> {
                try {
                    new Runner(nodeFactory).parse(language, context, source);
                } catch (Throwable t) {
                    throw new RuntimeException("Error while trying to parse dynamic library " + source.getName(), t);
                }
            });
        }
    }

    private static void handleParserResult(LLVMContext context, LLVMParserResult result) {
        context.registerGlobalVarInit(result.getGlobalVarInit());
        context.registerGlobalVarDealloc(result.getGlobalVarDealloc());
        if (result.getConstructorFunction() != null) {
            context.registerConstructorFunction(result.getConstructorFunction());
        }
        if (result.getDestructorFunction() != null) {
            context.registerDestructorFunction(result.getDestructorFunction());
        }
        if (!context.getEnv().getOptions().get(SulongEngineOption.PARSE_ONLY)) {
            try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                result.getGlobalVarInit().call(stackPointer);
            }
            if (result.getConstructorFunction() != null) {
                try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                    result.getConstructorFunction().call(stackPointer);
                }
            }
        }
    }

    public static void disposeContext(LLVMMemory memory, LLVMContext context) {
        LLVMFunctionDescriptor atexitDescriptor = context.getGlobalScope().getFunctionDescriptor("@__sulong_funcs_on_exit");
        if (atexitDescriptor != null) {
            RootCallTarget atexit = atexitDescriptor.getLLVMIRFunction();
            try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                atexit.call(stackPointer);
            }
        }
        for (RootCallTarget destructorFunction : context.getDestructorFunctions()) {
            try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                destructorFunction.call(stackPointer);
            }
        }
        for (RootCallTarget destructor : context.getGlobalVarDeallocs()) {
            try (StackPointer stackPointer = context.getThreadingStack().getStack().newFrame()) {
                destructor.call(stackPointer);
            }
        }
        context.getThreadingStack().freeMainStack(memory);
        context.getGlobalsStack().free();
    }

    private LLVMParserResult parseBitcodeFile(Source source, String libraryName, BitcodeParserResult bitcodeParserResult, LLVMLanguage language, LLVMContext context) {
        return LLVMParserRuntime.parse(source, libraryName, bitcodeParserResult, language, context, nodeFactory);
    }
}
