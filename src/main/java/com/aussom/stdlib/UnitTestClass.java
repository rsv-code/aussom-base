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

package com.aussom.stdlib;

import java.util.ArrayList;
import java.util.List;

public class UnitTestClass {
    protected String name = "";
    protected String className = "";

    // Class-level hooks fire once per class.
    protected String beforeFunctionName = "";
    protected String afterFunctionName = "";

    // Per-test hooks fire around every individual @Test method.
    protected String beforeEachFunctionName = "";
    protected String afterEachFunctionName = "";
    protected String onTestFailFunctionName = "";

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

    public void setBeforeEachFunctionName(String beforeEachFunctionName) {
        this.beforeEachFunctionName = beforeEachFunctionName;
    }

    public String getBeforeEachFunctionName() {
        return beforeEachFunctionName;
    }

    public void setAfterEachFunctionName(String afterEachFunctionName) {
        this.afterEachFunctionName = afterEachFunctionName;
    }

    public String getAfterEachFunctionName() {
        return afterEachFunctionName;
    }

    public void setOnTestFailFunctionName(String onTestFailFunctionName) {
        this.onTestFailFunctionName = onTestFailFunctionName;
    }

    public String getOnTestFailFunctionName() {
        return onTestFailFunctionName;
    }

    public boolean hasBeforeEach() {
        return !this.beforeEachFunctionName.trim().equals("");
    }

    public boolean hasAfterEach() {
        return !this.afterEachFunctionName.trim().equals("");
    }

    public boolean hasOnTestFail() {
        return !this.onTestFailFunctionName.trim().equals("");
    }
}
