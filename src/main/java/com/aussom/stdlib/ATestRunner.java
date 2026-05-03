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

import com.aussom.Environment;
import com.aussom.TestSecurityManagerImpl;
import com.aussom.ast.astAnnotation;
import com.aussom.ast.astClass;
import com.aussom.ast.astNode;
import com.aussom.ast.aussomException;
import com.aussom.types.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ATestRunner {
    protected UnitTestRunner runner = null;

    // Environment objects
    protected Members locals = null;
    protected Environment tenv = null;

    // Once a class instance is created it's stored here for next use.
    protected Map<String, AussomType> classObjects = new HashMap<String, AussomType>();

    public ATestRunner() throws Exception {
        // Create the new runner
        this.runner = new UnitTestRunner(new TestSecurityManagerImpl());

        // Allow running tests from Aussom
        ((TestSecurityManagerImpl)this.runner.getSecurityManager()).setAllowAussomTestRunner(true);

        // Add resource include path.
        this.runner.addResourceIncludePath("/com/aussom/stdlib/aus/");

        // Initialize the environment for the first time.
        this.resetEnvironment();
    }

    public void resetEnvironment() {
        // Reset the call stack
        this.runner.newMainCallstack();

        this.tenv = new Environment(this.runner);
        Members locals = new Members();
        this.tenv.setEnvironment(null, locals, this.runner.getMainCallStack());
    }

    public AussomType loadTestFile(Environment env, ArrayList<AussomType> args) throws Exception {
        String testFileName = ((AussomString)args.get(0)).getValue();
        this.runner.parseFile(testFileName);
        this.runner.loadTestClasses(testFileName);
        return env.getClassInstance();
    }

    public AussomType loadTestString(Environment env, ArrayList<AussomType> args) throws Exception {
        String testFileName = ((AussomString)args.get(0)).getValue();
        String testCode = ((AussomString)args.get(1)).getValue();
        this.runner.parseString(testFileName, testCode);
        this.runner.loadTestClasses(testFileName);
        return env.getClassInstance();
    }

    public AussomType getTestClasses(Environment env, ArrayList<AussomType> args) throws Exception {
        AussomList ret = new AussomList();
        for (UnitTestClass testClass : this.runner.getTestClasses()) {
            ret.add(new AussomString(testClass.getClassName()));
        }
        return ret;
    }

    public AussomType getTestFunctions(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        AussomList ret = new AussomList();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            for (UnitTest test : testClass.getTests()) {
                ret.add(new AussomString(test.getFunctionName()));
            }
        } else {
            return new AussomException("testRunner.getTestFunctions(): Provided class '" + className + "' not found.");
        }

        return ret;
    }

    public AussomType hasBefore(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            return new AussomBool(testClass.hasBefore());
        } else {
            return new AussomException("testRunner.hasBefore(): Provided class '" + className + "' not found.");
        }
    }

    public AussomType hasAfter(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            return new AussomBool(testClass.hasAfter());
        } else {
            return new AussomException("testRunner.hasAfter(): Provided class '" + className + "' not found.");
        }
    }

    public AussomType runBefore(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            if (testClass.hasBefore()) {
                // Run the @Before function
                astClass cls = this.runner.getClassByName(testClass.getClassName());
                AussomType tci = this.getClassObject(cls);
                if(!tci.isEx()) {
                    // Set the class instance and current object.
                    this.tenv.setCurObj(tci);

                    // Run the function
                    this.runner.runFunction(testClass, this.tenv, cls, testClass.getBeforeFunctionName());
                } else {
                    return tci;
                }
            } else {
                return new AussomException("testRunner.hasBefore(): Provided class '" + className + "' nas no @Before function set.");
            }
        } else {
            return new AussomException("testRunner.hasBefore(): Provided class '" + className + "' not found.");
        }
        return env.getClassInstance();
    }

    public AussomType runAfter(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            if (testClass.hasAfter()) {
                // Run the @After function
                astClass cls = this.runner.getClassByName(testClass.getClassName());
                AussomType tci = this.getClassObject(cls);
                if(!tci.isEx()) {
                    // Set the current object if it's null.
                    if (this.tenv.getCurObj() == null)
                        this.tenv.setCurObj(tci);

                    // Run the function
                    this.runner.runFunction(testClass, this.tenv, cls, testClass.getAfterFunctionName());
                } else {
                    return tci;
                }
            } else {
                return new AussomException("testRunner.hasAfter(): Provided class '" + className + "' has no @After function set.");
            }
        } else {
            return new AussomException("testRunner.hasAfter(): Provided class '" + className + "' not found.");
        }
        return env.getClassInstance();
    }

    public AussomType runTest(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        String functName = ((AussomString)args.get(1)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            // Run the @After function
            astClass cls = this.runner.getClassByName(testClass.getClassName());
            AussomType tci = this.getClassObject(cls);
            if(!tci.isEx()) {
                // Set the current object if it's null.
                if (this.tenv.getCurObj() == null)
                    this.tenv.setCurObj(tci);

                // Test methods are zero-arg by convention.
                astNode af = cls.getFunct(functName, "");
                if (af != null) {
                    astAnnotation functAnn = af.getAnnotation("Test");
                    if (functAnn != null) {
                        // Run the function
                        this.runner.runFunction(testClass, this.tenv, cls, functName);
                    } else {
                        return new AussomException("testRunner.runTest(): Provided class '" + className + "' function '" + functName + "' has no @Test annotation.");
                    }
                } else {
                    return new AussomException("testRunner.runTest(): Provided class '" + className + "' nas no function '" + functName + "'.");
                }
            } else {
                return tci;
            }
        } else {
            return new AussomException("testRunner.runTest(): Provided class '" + className + "' not found.");
        }
        return env.getClassInstance();
    }

    public AussomType clearClassObjectCache(Environment env, ArrayList<AussomType> args) {
        // Reset the class objects
        this.classObjects = new HashMap<String, AussomType>();
        return env.getClassInstance();
    }

    public AussomType hasBeforeEach(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            return new AussomBool(testClass.hasBeforeEach());
        } else {
            return new AussomException("testRunner.hasBeforeEach(): Provided class '" + className + "' not found.");
        }
    }

    public AussomType hasAfterEach(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            return new AussomBool(testClass.hasAfterEach());
        } else {
            return new AussomException("testRunner.hasAfterEach(): Provided class '" + className + "' not found.");
        }
    }

    public AussomType hasOnTestFail(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass != null) {
            return new AussomBool(testClass.hasOnTestFail());
        } else {
            return new AussomException("testRunner.hasOnTestFail(): Provided class '" + className + "' not found.");
        }
    }

    public AussomType getTestTags(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        String functName = ((AussomString)args.get(1)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass == null) {
            return new AussomException("testRunner.getTestTags(): Provided class '" + className + "' not found.");
        }
        for (UnitTest t : testClass.getTests()) {
            if (t.getFunctionName().equals(functName)) {
                AussomList ret = new AussomList();
                for (String tag : t.getTags()) ret.add(new AussomString(tag));
                return ret;
            }
        }
        return new AussomException("testRunner.getTestTags(): Function '" + functName + "' not found on class '" + className + "'.");
    }

    public AussomType getTestTimeoutMs(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        String functName = ((AussomString)args.get(1)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass == null) {
            return new AussomException("testRunner.getTestTimeoutMs(): Provided class '" + className + "' not found.");
        }
        for (UnitTest t : testClass.getTests()) {
            if (t.getFunctionName().equals(functName)) {
                return new AussomInt(t.getTimeoutMs());
            }
        }
        return new AussomException("testRunner.getTestTimeoutMs(): Function '" + functName + "' not found on class '" + className + "'.");
    }

    public AussomType setIncludeTags(Environment env, ArrayList<AussomType> args) throws Exception {
        AussomList tags = (AussomList)args.get(0);
        Set<String> set = new LinkedHashSet<String>();
        for (AussomType t : tags.getValue()) set.add(((AussomString)t).getValue());
        this.runner.setIncludeTags(set);
        return env.getClassInstance();
    }

    public AussomType setExcludeTags(Environment env, ArrayList<AussomType> args) throws Exception {
        AussomList tags = (AussomList)args.get(0);
        Set<String> set = new LinkedHashSet<String>();
        for (AussomType t : tags.getValue()) set.add(((AussomString)t).getValue());
        this.runner.setExcludeTags(set);
        return env.getClassInstance();
    }

    public AussomType runClassTests(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass == null) {
            return new AussomException("testRunner.runClassTests(): Provided class '" + className + "' not found.");
        }
        UnitTestResult result = this.runner.runClass(testClass);
        return result.toAussomType();
    }

    public AussomType shouldRun(Environment env, ArrayList<AussomType> args) throws Exception {
        String className = ((AussomString)args.get(0)).getValue();
        String functName = ((AussomString)args.get(1)).getValue();
        UnitTestClass testClass = this.runner.getTestClassByName(className);
        if (testClass == null) {
            return new AussomException("testRunner.shouldRun(): Provided class '" + className + "' not found.");
        }
        for (UnitTest t : testClass.getTests()) {
            if (t.getFunctionName().equals(functName)) {
                return new AussomBool(this.runner.shouldRun(t));
            }
        }
        return new AussomException("testRunner.shouldRun(): Function '" + functName + "' not found on class '" + className + "'.");
    }

    protected AussomType getClassObject(astClass cls) throws aussomException {
        if (this.classObjects.containsKey(cls.getName()))
            return (AussomType)this.classObjects.get(cls.getName());
        else
            return cls.instantiate(this.tenv, false, new AussomList());
    }
}
