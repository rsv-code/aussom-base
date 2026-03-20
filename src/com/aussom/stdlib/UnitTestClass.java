package com.aussom.stdlib;

import java.util.ArrayList;
import java.util.List;

public class UnitTestClass {
    protected String name = "";
    protected String className = "";

    protected String beforeFunctionName = "";
    protected String afterFunctionName = "";

    protected List<UnitTest> tests = new ArrayList<UnitTest>();

    public UnitTestClass() {}
    public UnitTestClass(String className) {
        this.className = className;
    }
    public UnitTestClass(String className, String name) {
        this.className = className;
        this.name = name;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getClassName() {
        return className;
    }
    public void setClassName(String className) {
        this.className = className;
    }
    public List<UnitTest> getTests() {
        return tests;
    }
    public void setTests(List<UnitTest> tests) {
        this.tests = tests;
    }
    public void addTest(UnitTest test) {
        this.tests.add(test);
    }

    public String getTestDisplayString() {
        String ret = "Running Test [ " + this.className + " : ";
        if (this.name != null &&  !this.name.equals("")) {
            ret += this.name + " ]";
        } else {
            ret += "class tests ]";
        }
        return ret;
    }

    public void setBeforeFunctionName(String beforeFunctionName) {
        this.beforeFunctionName = beforeFunctionName;
    }

    public String getBeforeFunctionName() {
        return beforeFunctionName;
    }

    public void setAfterFunctionName(String afterFunctionName) {
        this.afterFunctionName = afterFunctionName;
    }

    public String getAfterFunctionName() {
        return afterFunctionName;
    }

    public boolean hasBefore() {
        return !this.beforeFunctionName.trim().equals("");
    }

    public boolean hasAfter() {
        return !this.afterFunctionName.trim().equals("");
    }
}
