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
