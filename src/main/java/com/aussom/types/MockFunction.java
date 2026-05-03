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

import java.util.ArrayList;
import java.util.List;

public class MockFunction {
    protected String functionName = "";
    protected MockFunctionType mockFunctionType = MockFunctionType.SIMPLE;

    /**
     * The return value for the mocked function.
     */
    protected AussomObject returnValue = null;

    /**
     * For condition for a when mock.
     */
    protected AussomCallback condition = null;

    /**
     * The spy flag for the function. If set to true records should
     * be added to spyRecords on each call.
     */
    protected boolean spy = false;

    /**
     * A list of spy records to keep track of when the
     * mocked function is called.
     */
    protected List<MockFunctionSpyRecord> spyRecords = new ArrayList<MockFunctionSpyRecord>();

    /**
     * Spy only doesn't intercept the function but just logs
     * the call information in the spyRecords.
     * @param functionName
     */
    public MockFunction(String functionName) {
        this.mockFunctionType = MockFunctionType.SPY_ONLY;
        this.spy = true;
        this.functionName = functionName;
    }

    /**
     * Simple mock constructor.
     * @param functionName is a String with the function to mock.
     * @param returnValue is an AussomObject with the value to return.
     */
    public MockFunction(String functionName, AussomObject returnValue) {
        this.mockFunctionType = MockFunctionType.SIMPLE;
        this.functionName = functionName;
        this.returnValue = returnValue;
    }

    /**
     * When condition mock constructor.
     * @param functionName is a String with the function to mock.
     * @param condition is a AussomCallback the evaluates to return the returnValue
     *                  or not. The callback must return 1 or true to ensure returnValue
     *                  is returned.
     * @param returnValue is an AussomObject with the value to return.
     */
    public MockFunction(String functionName, AussomCallback condition, AussomObject returnValue) {
        this.mockFunctionType = MockFunctionType.WHEN;
        this.functionName = functionName;
        this.condition = condition;
        this.returnValue = returnValue;
    }

    /**
     * Adds a spy record.
     * @param spyRecord is the record to add.
     */
    public void addMockFunctionSpyRecord(MockFunctionSpyRecord spyRecord) {
        this.spyRecords.add(spyRecord);
    }

    /**
     * Gets the list of spy records.
     * @return A list of spy records.
     */
    public List<MockFunctionSpyRecord> getSpyRecords() {
        return spyRecords;
    }

    /**
     * Sets the spy flag.
     * @param spy is a boolean with the spy flag.
     */
    public void setSpy(boolean spy) {
        this.spy = spy;
    }

    /**
     * Gets the spy flag.
     * @return A boolean with the spy flag.
     */
    public boolean isSpy() {
        return spy;
    }

    /**
     * Gest the spy flag.
     * @return A spy flag.
     */
    public MockFunctionType getMockFunctionType() {
        return mockFunctionType;
    }

    /**
     * Get mock function name.
     * @return A string with the mock function name.
     */
    public String getFunctionName() {
        return functionName;
    }

    /**
     * Gets the return value for the mock.
     * @return An AussomObject with the return value.
     */
    public AussomObject getReturnValue() {
        return returnValue;
    }

    /**
     * Gets the condition for the mock.
     * @return An AussomCallback object with the condition.
     */
    public AussomCallback getCondition() {
        return condition;
    }
}
