package com.aussom.stdlib;

import com.aussom.*;
import com.aussom.ast.astAnnotation;
import com.aussom.ast.astClass;
import com.aussom.ast.astNode;
import com.aussom.ast.aussomException;
import com.aussom.types.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UnitTestRunner extends Engine {
    protected String name = "";
    protected String description = "";
    protected List<UnitTestClass> testClasses = new ArrayList<UnitTestClass>();

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
            long start = (new Date()).getTime();
            UnitTestResult res = this.runTestClasses();
            long end = (new Date()).getTime();
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
        if(!tci.isEx())
        {
            AussomObject classInstance = (AussomObject) tci;
            tenv.setClassInstance(classInstance);
            tenv.setCurObj(classInstance);

            try {
                // Run before if it exists
                if (!testClass.getBeforeFunctionName().equals("")) {
                    this.runFunction(testClass, tenv, cls, testClass.getBeforeFunctionName());
                }

                for (UnitTest test : testClass.getTests()) {
                    result.total++;
                    astNode af = cls.getFunct(test.getFunctionName());
                    astAnnotation functAnn = af.getAnnotation("Test");

                    String testLogStr = test.getTestDisplayString();
                    if (test.getSkip()) {
                        result.skipped++;
                        testLogStr += "SKIPPED";
                    } else {
                        int tret = 0;

                        try {
                            tret = this.runFunction(testClass, tenv, cls, test.getFunctionName());

                            if (tret != 0) {
                                result.failed++;
                                testLogStr += "FAILED";
                            } else {
                                result.passed++;
                                testLogStr += "PASSED";
                            }
                        } catch (aussomException e) {
                            result.failed++;
                            testLogStr += "FAILED\n" + e.getMessage();
                        }
                    }
                    console.get().info(testLogStr);
                }

                // Run before if it exists
                if (!testClass.getAfterFunctionName().equals("")) {
                    this.runFunction(testClass, tenv, cls, testClass.getAfterFunctionName());
                }
            } catch (Exception e) {
                console.get().err(Util.stackTraceToString(e));
            }
        } else {
            AussomException ex = (AussomException) tci;
            console.get().err(ex.toString());
        }

        return result;
    }

    public int runFunction(UnitTestClass testClass, Environment tenv, astClass cls, String functName) throws aussomException {
        /*
         * Main is expecting a list of args, but the function is expecting
         * a list as well, so list inside of list.
         */
        AussomList margs = new AussomList();
        margs.add(new AussomList(false));

        /*
         * Call the function.
         */
        AussomType ret = new AussomNull();
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
