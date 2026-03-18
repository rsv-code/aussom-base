package com.aussom.stdlib;

public class UnitTest {
    protected String name = "";
    protected String functionName = "";
    protected boolean skip = false;

    public UnitTest() { }
    public UnitTest(String functionName) {
        this.functionName = functionName;
    }
    public UnitTest(String name, String functionName) {
        this.name = name;
        this.functionName = functionName;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getFunctionName() {
        return functionName;
    }
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
    public boolean getSkip() {
        return skip;
    }
    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public String getTestDisplayString() {
        String ret = "*** " + this.functionName + " : ";
        if (this.name != null &&  !this.name.equals("")) {
            ret += this.name + " ... ";
        } else {
            ret += "unit test ... ";
        }
        return ret;
    }
}
