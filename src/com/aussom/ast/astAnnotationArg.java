package com.aussom.ast;

import com.aussom.Environment;
import com.aussom.types.AussomType;

/**
 * Holds the annotation key value pair.
 */
public class astAnnotationArg extends astNode implements astNodeInt {
    protected String key = "";
    protected String value = "";

    public astAnnotationArg() {}

    public astAnnotationArg(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public String toString(int Level) {
        String rstr = "";
        rstr += getTabs(Level) + "{ " + this.key + ": \"" + this.value + "\" }\n";
        return rstr;
    }

    @Override
    public AussomType evalImpl(Environment env, boolean getref) throws aussomException {
        return null;
    }
}
