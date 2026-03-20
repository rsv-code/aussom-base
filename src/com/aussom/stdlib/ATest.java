package com.aussom.stdlib;

import com.aussom.Environment;
import com.aussom.Util;
import com.aussom.ast.astClass;
import com.aussom.types.*;

import java.util.ArrayList;

public class ATest {
    public static AussomType runTestsForClass(Environment env, ArrayList<AussomType> args) throws Exception {
        String testClassName = ((AussomString)args.get(0)).getValue();

        // First check that the security manager property
        // test.aussom.runner is set to true.
        if (!(Boolean)env.getEngine().getSecurityManager().getProperty("test.aussom.runner")) {
            return new AussomException("testRunner.runTestsForClass(): Security exception, action 'test.aussom.runner' not permitted.");
        }

        astClass lcls = env.getEngine().getClassByName(testClassName);
        String ScriptFile = lcls.getFileName();

        if  (lcls != null) {
            // If we made it here we're allowed to run it.
            UnitTestRunner testRunner = new UnitTestRunner(ScriptFile, "Run unit tests");

            // Add resource include path.
            testRunner.addResourceIncludePath("/com/aussom/stdlib/aus/");

            // Parse the provided file name.
            testRunner.parseFile(ScriptFile);

            // Load the test classes for the provided script file.
            testRunner.loadTestClasses(ScriptFile);

            astClass cls = testRunner.getClassByName(testClassName);

            try {
                UnitTestResult result = testRunner.runClass(cls.getTestClass());
                return result.toAussomType();
            } catch (Exception e) {
                console.get().err(Util.stackTraceToString(e));
                throw e;
            }
        } else {
            return new AussomException("test.runTestsForClass(): Provided class '" + testClassName + "' not found.");
        }
    }
}
