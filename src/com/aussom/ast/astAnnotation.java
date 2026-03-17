package com.aussom.ast;

import com.aussom.Environment;
import com.aussom.types.AussomType;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the annotation information.
 */
public class astAnnotation extends astNode implements astNodeInt {
    protected String name = "";
    protected List<astAnnotationArg> args = new ArrayList<astAnnotationArg>();

    public astAnnotation() { }

    public astAnnotation(String name) {
        this.name = name;
    }

    public astAnnotation(String name, List<astAnnotationArg> args) {
        this.name = name;
        this.args = args;
    }

    public void addArg(astAnnotationArg arg) {
        this.args.add(arg);
    }

    public List<astAnnotationArg> getArgs() {
        return args;
    }

    public void setArgs(List<astAnnotationArg> args) {
        this.args = args;
    }

    @Override
    public String toString(int Level) {
        String rstr = "";
        rstr += getTabs(Level) + "{\n";
        rstr += getTabs(Level + 1) + "\"name\": \"" + this.name + "\",\n";
        rstr += getTabs(Level + 1) + "\"args\": {\n";
        for (astAnnotationArg arg : this.args) {
            rstr += arg.toString(Level + 2);
        }
        rstr += getTabs(Level + 1) + "}\n";
        return rstr;
    }

    @Override
    public AussomType evalImpl(Environment env, boolean getref) throws aussomException {
        return null;
    }
}
