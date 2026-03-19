package com.aussom.types;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MockFunctionSpyRecord {
    /*
     * Time in milliseconds since epoch of the spy to order
     * in time.
     */
    protected long timestamp = 0;

    /**
     * Holds the evaled call args.
     */
    protected List<AussomObject> callArgs = new ArrayList<AussomObject>();

    /**
     * Holds the function return object, defaulted to null.
     */
    protected AussomObject returnValue = new AussomNull();

    public MockFunctionSpyRecord(List<AussomObject> callArgs, AussomObject returnValue) {
        this.timestamp = (new Date()).getTime();
        this.callArgs = callArgs;
        this.returnValue = returnValue;
    }

    public long getTimestamp() { return timestamp; }

    public List<AussomObject> getCallArgs() {
        return callArgs;
    }

    public AussomObject getReturnValue() {
        return returnValue;
    }
}
