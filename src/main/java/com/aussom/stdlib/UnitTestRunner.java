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

import com.aussom.*;
import com.aussom.ast.astAnnotation;
import com.aussom.ast.astClass;
import com.aussom.ast.astNode;
import com.aussom.ast.aussomException;
import com.aussom.types.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UnitTestRunner extends Engine {
    protected String name = "";
    protected String description = "";
    protected List<UnitTestClass> testClasses = new ArrayList<UnitTestClass>();

    // When non-empty, only tests whose tag set intersects includeTags
    // are run. Untagged tests are excluded. Empty means "no include
    // filter" — i.e. all tests are eligible.
    protected Set<String> includeTags = new LinkedHashSet<String>();

    // When non-empty, tests whose tag set intersects excludeTags are
    // skipped. Exclude wins over include if both match.
    protected Set<String> excludeTags = new LinkedHashSet<String>();

    public UnitTestRunner(SecurityManagerInt SecurityManager) throws Exception {
        super(SecurityManager);
    }
    public UnitTestRunner(SecurityManagerInt SecurityManager, String name) throws Exception {
        super(SecurityManager);
        this.name = name;
    }
    public UnitTestRunner(SecurityManagerInt SecurityManager, String name, String description) throws Exception {
        super(SecurityManager);
        this.name = name;
        this.description = description;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }
    public List<UnitTestClass> getTestClasses() {
        return testClasses;
    }
    public void setTestClasses(List<UnitTestClass> testClasses) {
        this.testClasses = testClasses;
    }
    public void addTestClass(UnitTestClass testClass) {
        this.testClasses.add(testClass);
    }

    /**
     * Adds an individual test class to be tested.
     * @param cls is the class to test.
     */
    public void addClassToTest(astClass cls) {
        this.addTestClass(cls.getTestClass());
    }

    public UnitTestClass getTestClassByName(String className) {
        for (UnitTestClass testClass : this.testClasses) {
            if (testClass.getClassName().equals(className)) return testClass;
        }
        return null;
    }

    public Set<String> getIncludeTags() {
        return this.includeTags;
    }
    public void setIncludeTags(Set<String> includeTags) {
        this.includeTags = (includeTags == null) ? new LinkedHashSet<String>() : includeTags;
    }
    public Set<String> getExcludeTags() {
        return this.excludeTags;
    }
    public void setExcludeTags(Set<String> excludeTags) {
        this.excludeTags = (excludeTags == null) ? new LinkedHashSet<String>() : excludeTags;
    }

    /**
     * Returns true when a test should run given the current
     * include/exclude tag filters. When excludeTags is non-empty and
     * the test has at least one tag in it, the test is skipped.
     * When includeTags is non-empty, the test must have at least one
     * matching tag (untagged tests are skipped). When both filters
     * are empty, all tests run.
     */
    public boolean shouldRun(UnitTest test) {
        Set<String> testTags = test.getTags();
        if (!this.excludeTags.isEmpty()) {
            for (String t : testTags) {
                if (this.excludeTags.contains(t)) return false;
            }
        }
        if (!this.includeTags.isEmpty()) {
            for (String t : testTags) {
                if (this.includeTags.contains(t)) return true;
            }
            return false;
        }
        return true;
    }

    /**
     * Loads the test classes from the provided script file name.
     * @param scriptFile is a String with the script file to load from.
     * @throws aussomException
     */
    public void loadTestClasses(String scriptFile) throws aussomException {
        // Get all classes associated with that file.
        List<astClass> testClasses = this.getClassByFileNameAndPath(scriptFile);
        console.get().trc("Found " + testClasses.size() + " test classes for script '" + scriptFile + "'.");

        if (testClasses.size() == 0) {
            throw new aussomException("Engine.runTest(): No classes found for that script file.");
        }

        // Now look for tests.
        for (astClass cls : testClasses) {
            if (cls.containsTests()) {
                // Success, a test was found!
                this.addTestClass(cls.getTestClass());
            }
        }
    }

    /**
     * Loads all test classes.
     * @throws aussomException
     */
    public void loadAllTestClasses() throws aussomException {
        // Get all classes associated with that file.
        for (astClass cls : this.getClasses().values()) {
            if (cls.containsTests()) {
                this.addTestClass(cls.getTestClass());
            }
        }
        console.get().trc("Found " + this.testClasses.size() + " test classes.");

        if (this.testClasses.size() == 0) {
            throw new aussomException("Engine.runTest(): No classes found for that script file.");
        }
    }

    /**
     * Runs the unit tests in the provide script file name.
     * @throws aussomException on failure to find main class or on parse errors.
     * @return An integer with 0 for success and any other value for failure.
     */
    public int runTests() throws aussomException {
        if (!this.hasParseErrors()) {
            console.get().trc("Running tests now ...");

            console.get().log("");
            console.get().info("**************************************************************");
            console.get().info("RUNNING TESTS");
            console.get().info("**************************************************************");

            // Finally run the tests
            long start = System.currentTimeMillis();
            UnitTestResult res = this.runTestClasses();
            long end = System.currentTimeMillis();
            double elapsedSeconds = (end - start)/1000.0;

            console.get().log("");
            console.get().info("**************************************************************");
            console.get().info("PASSED: " +  res.passed + " SKIPPED: " + res.skipped + " FAILED: " + res.failed + " TOTAL: " + res.total);
            console.get().info("Elapsed: " + elapsedSeconds + "s");
            console.get().info("**************************************************************\n");

            if (res.failed > 0) {
                return 1;
            } else {
                return 0;
            }
        } else {
            throw new aussomException("Engine.runTest(): Parse errors were encountered. Not running.");
        }
    }

    public UnitTestResult runTestClasses() {
        UnitTestResult result = new UnitTestResult();

        for (UnitTestClass testClass : testClasses) {
            console.get().log("");
            console.get().info(testClass.getTestDisplayString());
            try {
                UnitTestResult tret = this.runClass(testClass);
                result.merge(tret);
            } catch (aussomException e) {
                console.get().err(Util.stackTraceToString(e));
            }
        }

        return result;
    }

    public UnitTestResult runClass(UnitTestClass testClass) throws aussomException {
        UnitTestResult result = new UnitTestResult();

        // Reset the call stack
        this.newMainCallstack();

        Environment tenv = new Environment(this);
        Members locals = new Members();
        tenv.setEnvironment(null, locals, this.getMainCallStack());

        astClass cls = this.getClassByName(testClass.getClassName());
        AussomType tci = cls.instantiate(tenv, false, new AussomList());
        if(!tci.isEx()) {
            AussomObject classInstance = (AussomObject) tci;
            tenv.setClassInstance(classInstance);
            tenv.setCurObj(classInstance);

            // Run before if it exists
            if (!testClass.getBeforeFunctionName().equals("")) {
                this.runFunction(testClass, tenv, cls, testClass.getBeforeFunctionName());
            }

            for (UnitTest test : testClass.getTests()) {
                result.total++;
                String testLogStr = test.getTestDisplayString();

                try {

                    astNode af = cls.getFunct(test.getFunctionName(), "");
                    astAnnotation functAnn = af.getAnnotation("Test");
                } catch (Exception e) {
                    result.failed++;
                    testLogStr += "FAILED\n" + e.getMessage();
                }

                if (test.getSkip()) {
                    result.skipped++;
                    testLogStr += "SKIPPED";
                } else if (!this.shouldRun(test)) {
                    result.skipped++;
                    testLogStr += "SKIPPED (filtered)";
                } else {
                    boolean passed = false;
                    String failMsg = "";

                    // @BeforeEach: failure here marks the test failed and
                    // skips the test body, but @OnTestFail and @AfterEach
                    // still run.
                    boolean beforeEachOk = true;
                    if (testClass.hasBeforeEach()) {
                        try {
                            this.runFunction(testClass, tenv, cls, testClass.getBeforeEachFunctionName());
                        } catch (aussomException e) {
                            beforeEachOk = false;
                            failMsg = "@BeforeEach threw: " + e.getMessage();
                        }
                    }

                    if (beforeEachOk) {
                        try {
                            int tret = this.runFunction(testClass, tenv, cls, test.getFunctionName());
                            passed = (tret == 0);
                            if (!passed) failMsg = "test returned non-zero";
                        } catch (aussomException e) {
                            passed = false;
                            failMsg = e.getMessage();
                        }
                    }

                    // @OnTestFail: best-effort. A throw here is logged
                    // but does not change the recorded result.
                    if (!passed && testClass.hasOnTestFail()) {
                        try {
                            this.runFunction(testClass, tenv, cls, testClass.getOnTestFailFunctionName());
                        } catch (aussomException e) {
                            console.get().err("@OnTestFail threw: " + e.getMessage());
                        }
                    }

                    // @AfterEach: best-effort. Always runs.
                    if (testClass.hasAfterEach()) {
                        try {
                            this.runFunction(testClass, tenv, cls, testClass.getAfterEachFunctionName());
                        } catch (aussomException e) {
                            console.get().err("@AfterEach threw: " + e.getMessage());
                        }
                    }

                    if (passed) {
                        result.passed++;
                        testLogStr += "PASSED";
                    } else {
                        result.failed++;
                        testLogStr += "FAILED";
                        if (failMsg != null && !failMsg.equals("")) {
                            testLogStr += "\n" + failMsg;
                        }
                    }
                }
                console.get().info(testLogStr);
            }

            // Run before if it exists
            if (!testClass.getAfterFunctionName().equals("")) {
                this.runFunction(testClass, tenv, cls, testClass.getAfterFunctionName());
            }
        } else {
            AussomException ex = (AussomException) tci;
            console.get().err(ex.toString());
        }

        return result;
    }

    public int runFunction(UnitTestClass testClass, Environment tenv, astClass cls, String functName) throws aussomException {
        /*
         * @Test, @Before, and @After test methods are zero-arg by
         * convention; pass an empty argument list so the
         * dispatcher resolves to the zero-arg overload.
         */
        AussomList margs = new AussomList();

        AussomType ret;
        ret = cls.call(tenv, false, functName, margs);
        if(ret.isEx()) {
            AussomException ex = (AussomException) ret;
            throw new aussomException(ex.stackTraceToString());
        } else if (ret instanceof AussomInt) {
            return (int)((AussomInt)ret).getNumericInt();
        }
        return 0;
    }
}
