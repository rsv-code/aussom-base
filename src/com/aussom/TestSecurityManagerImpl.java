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
}
