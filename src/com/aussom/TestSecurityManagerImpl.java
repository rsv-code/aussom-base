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
