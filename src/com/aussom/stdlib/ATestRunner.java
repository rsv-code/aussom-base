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
import java.util.Map;

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
                    // Set the current object.
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
                    // Set the current object.
                    this.tenv.setCurObj(tci);

                    // Run the function
                    this.runner.runFunction(testClass, this.tenv, cls, testClass.getAfterFunctionName());
                } else {
                    return tci;
                }
            } else {
                return new AussomException("testRunner.hasAfter(): Provided class '" + className + "' nas no @After function set.");
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
            if (testClass.hasAfter()) {
                // Run the @After function
                astClass cls = this.runner.getClassByName(testClass.getClassName());
                AussomType tci = this.getClassObject(cls);
                if(!tci.isEx()) {
                    // Set the current object.
                    this.tenv.setCurObj(tci);

                    astNode af = cls.getFunct(functName);
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
                return new AussomException("testRunner.runTest(): Provided class '" + className + "' nas no @After function set.");
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

    protected AussomType getClassObject(astClass cls) throws aussomException {
        if (this.classObjects.containsKey(cls.getName()))
            return (AussomType)this.classObjects.get(cls.getName());
        else
            return cls.instantiate(this.tenv, false, new AussomList());
    }
}
