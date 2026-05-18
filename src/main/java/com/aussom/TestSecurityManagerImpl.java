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

package com.aussom;

public class TestSecurityManagerImpl extends SecurityManagerImpl {
    public TestSecurityManagerImpl() {

        /*
         * Aussomdoc actions. See com.aussom.stdlib.ALang.java.
         */
        this.props.put("aussomdoc.file.getJson", true);
        this.props.put("aussomdoc.class.getJson", true);

        /*
         * Unit testing actions.
         */
        this.props.put("test.mock.inject", true);
        this.props.put("test.mock.spy", true);

        /*
         * Script mode actions. Enabled in test contexts so the
         * ScriptMode JUnit suite can exercise Engine.setScriptMode
         * and Engine.evalLine.
         */
        this.props.put("aussom.script.mode.enable", true);

        /*
         * Debugger attach. Enabled in test contexts so the
         * Debugger JUnit suite can exercise Engine.setDebugger.
         */
        this.props.put("aussom.debugger.enable", true);
    }

    /**
     * Allows setting of test.aussom.runner property.
     * @param allowAussomTestRunner is a boolean with true to enable
     *                              and false to disable.
     */
    public void setAllowAussomTestRunner(boolean allowAussomTestRunner) {
        this.props.put("test.aussom.runner", allowAussomTestRunner);
    }
}
