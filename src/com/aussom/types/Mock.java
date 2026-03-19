package com.aussom.types;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class Mock {
    /**
     * Weather or not the mock is set on this object. True is
     * set and false is not.
     */
    protected boolean mockSet = false;

    /**
     * A list of mocked functions.
     */
    protected List<MockFunction> mockFunctions = new ArrayList<MockFunction>();

    /**
     * Default constructor.
     */
    public Mock() { }

    /**
     * Check if mocks are set. This is to keep the check for mocks
     * as little as possible.
     * @return A booleans with true if any mocks are set for this
     * object and false if not.
     */
    public boolean isMockSet() {
        return this.mockSet;
    }

    /**
     * Checks to see if there is a mock of type SIMPLE or WHEN for the
     * provided function name.
     * @param functionName is a String with the function name to check for.
     * @return A boolean with true if there is a function mock.
     */
    public boolean hasFunctionMock(String functionName) {
        for (MockFunction mockFunction : mockFunctions) {
            if (mockFunction.getFunctionName().equals(functionName)) {
                if (mockFunction.getMockFunctionType() == MockFunctionType.SIMPLE
                        ||  mockFunction.getMockFunctionType() == MockFunctionType.WHEN) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Looks for the first simple mock for the provided function name. If found
     * it returns it and if not it returns null.
     * @param functionName is a string with the function name to look for.
     * @return A MockFunction object or null if not found.
     */
    public MockFunction getSimpleMock(String functionName) {
        for (MockFunction mockFunction : mockFunctions) {
            if (mockFunction.getFunctionName().equals(functionName) && mockFunction.getMockFunctionType() == MockFunctionType.SIMPLE) {
                return mockFunction;
            }
        }
        return null;
    }

    /**
     * Gets a list of all the MockFunction objects with that function name.
     * @param functionName is a string with the function name to look for.
     * @return A List of MockFunction objects.
     */
    public List<MockFunction> getMockFunctions(String functionName) {
        List<MockFunction> ret = new ArrayList<MockFunction>();
        for (MockFunction mockFunction : mockFunctions) {
            if (mockFunction.getFunctionName().equals(functionName)) {
                ret.add(mockFunction);
            }
        }
        return ret;
    }

    /**
     * Sets the spy flag for the provided function name. If there's already
     * a mock for this function, it just sets the flag. Otherwise, it creates
     * a spy only MockFunction record and adds it.
     * @param functionName is the function name to spy on.
     */
    public void setSpy(String functionName) {
        MockFunction mf = null;
        for (MockFunction mockFunction : mockFunctions) {
            if (mockFunction.functionName.equals(functionName)) {
                mf = mockFunction;
                break;
            }
        }
        if (mf != null) {
            mf.setSpy(true);
        } else {
            // Create a spy only mock function record.
            mf = new MockFunction(functionName);
            this.mockFunctions.add(mf);
        }
    }

    /**
     * Sets a simple function mock which just returns the provided
     * return value when the function is invoked.
     * @param functionName is the name of the function to mock.
     * @param returnValue is any Aussom object to return when the
     *                    function is called.
     */
    public void setFunctionMock(String functionName, AussomObject returnValue) {
        this.mockSet = true;
        MockFunction mockFunction = new MockFunction(functionName, returnValue);
        this.mockFunctions.add(mockFunction);
    }

    /**
     * Adds a when (conditional) mock function.
     * @param functionName is the name of the function to mock.
     * @param condition is an Aussom callback with the condition.
     * @param returnValue is any Aussom object to return when the
     */
    public void setWhenFunctionMock(String functionName, AussomCallback condition, AussomObject returnValue) {
        this.mockSet = true;
        MockFunction mockFunction = new MockFunction(functionName, condition, returnValue);
        this.mockFunctions.add(mockFunction);
    }

    public List<MockFunctionSpyRecord> getSpyResults(String functionName) {
        List<MockFunctionSpyRecord> spyRecords = new ArrayList<MockFunctionSpyRecord>();

        for (MockFunction mockFunction : mockFunctions) {
            if (mockFunction.functionName.equals(functionName)) {
                if (mockFunction.isSpy()) {
                    spyRecords.addAll(mockFunction.getSpyRecords());
                }
            }
        }

        return spyRecords;
    }
}
