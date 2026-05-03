/*
 * Copyright 2026 Austin Lehman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aussom.types;

public class MockFunctionSpyRecord {
    /*
     * Time in milliseconds since epoch of the spy to order
     * in time.
     */
    protected long timestamp = 0;

    /**
     * Holds the evaled call args.
     */
    protected AussomList callArgs = new AussomList();

    /**
     * Holds the function return object, defaulted to null.
     */
    protected AussomObject returnValue = new AussomNull();

    public MockFunctionSpyRecord(AussomList callArgs, AussomObject returnValue) {
        this.timestamp = System.currentTimeMillis();
        this.callArgs = callArgs;
        this.returnValue = returnValue;
    }

    public long getTimestamp() { return timestamp; }

    public AussomList getCallArgs() {
        return callArgs;
    }

    public AussomObject getReturnValue() {
        return returnValue;
    }
}
