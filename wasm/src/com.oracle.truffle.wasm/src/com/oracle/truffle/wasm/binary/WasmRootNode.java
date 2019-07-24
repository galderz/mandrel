/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;

@NodeInfo(language = "wasm", description = "The root node of all WebAssembly functions")
public class WasmRootNode extends RootNode implements WasmNodeInterface {
    @CompilationFinal private WasmCodeEntry codeEntry;
    @Child private WasmBlockNode body;

    public WasmRootNode(TruffleLanguage<?> language, WasmCodeEntry codeEntry) {
        super(language);
        this.codeEntry = codeEntry;
        this.body = null;
    }

    public void setBody(WasmBlockNode body) {
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        /*
         * WebAssembly structure dictates that a function's arguments are provided to the function
         * as local variables, followed by any additional local variables that the function declares.
         * A VirtualFrame contains a special array for the arguments, so we need to move them to the locals.
         */
        argumentsToLocals(frame);

        // TODO: Accessing the context like this seems to be quite slow.
        body.execute(WasmContext.getCurrent(), frame);

        long returnValue = pop(frame, 0);
        switch (body.returnTypeId()) {
            case 0x00:
            case ValueTypes.VOID_TYPE:
                return WasmVoidResult.getInstance();
            case ValueTypes.I32_TYPE:
                Assert.assertEquals(returnValue >>> 32, 0, "Expected i32 value, popped value was larger than 32 bits.");
                return (int) returnValue;
            case ValueTypes.I64_TYPE:
                return returnValue;
            case ValueTypes.F32_TYPE:
                Assert.assertEquals(returnValue >>> 32, 0, "Expected f32 value, popped value was larger than 32 bits.");
                return Float.intBitsToFloat((int) returnValue);
            case ValueTypes.F64_TYPE:
                return Double.longBitsToDouble(returnValue);
            default:
                Assert.fail(String.format("Unknown type: 0x%02X", body.returnTypeId()));
                return null;
        }
    }

    private void argumentsToLocals(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        for (int i = 0; i != args.length; ++i) {
            FrameSlot slot = codeEntry.localSlot(i);
            FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(slot);
            switch (kind) {
                case Int:
                    frame.setInt(slot, (int) args[i]);
                    break;
                case Long:
                    frame.setLong(slot, (long) args[i]);
                    break;
                case Float:
                    frame.setFloat(slot, (float) args[i]);
                    break;
                case Double:
                    frame.setDouble(slot, (double) args[i]);
                    break;
            }
        }
    }

    @Override
    public WasmCodeEntry codeEntry() {
        return codeEntry;
    }

    @Override
    public String toString() {
        return "wasm-function-" + codeEntry.functionIndex();
    }
}
