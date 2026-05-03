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

package com.aussom.ast;

import com.aussom.Environment;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the annotation information.
 */
public class astAnnotation extends astNode implements astNodeInt {
    protected String annotationName = "";
    protected List<astAnnotationArg> args = new ArrayList<astAnnotationArg>();

    public astAnnotation() { }

    public astAnnotation(String annotationName) {
        this.annotationName = annotationName;
    }

    public astAnnotation(String annotationName, List<astAnnotationArg> args) {
        this.annotationName = annotationName;
        this.args = args;
    }

    public void setAnnotationName(String annotationName) {
        this.annotationName = annotationName;
    }

    public String getAnnotationName() {
        return annotationName;
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

    public List<astAnnotationArg> getAnnotationArgsByName(String annotationName) {
        List<astAnnotationArg> ret = new ArrayList<astAnnotationArg>();
        for (astAnnotationArg arg : this.args) {
            if (arg.getKey().equals(annotationName)) {
                ret.add(arg);
            }
        }
        return ret;
    }

    public String getAnnotationArgValueByName(String annotationName) {
        for (astAnnotationArg arg : this.args) {
            if (arg.getKey().equals(annotationName)) {
                return arg.getValue();
            }
        }
        return null;
    }

    public AussomType getAussomType() {
        AussomMap mp = new AussomMap();

        mp.put("annotationName", new AussomString(this.annotationName));

        AussomMap argsMap = new AussomMap();
        for (astAnnotationArg arg : this.args) {
            argsMap.put(arg.getKey(), new AussomString(arg.getValue()));
        }

        mp.put("annotationArgs", argsMap);

        return mp;
    }

    @Override
    public String toString() {
        return this.toString(0);
    }

    @Override
    public String toString(int Level) {
        String rstr = "";
        rstr += getTabs(Level) + "{\n";
        rstr += getTabs(Level + 1) + "\"name\": \"" + this.annotationName + "\",\n";
        rstr += getTabs(Level + 1) + "\"args\": {\n";
        for (astAnnotationArg arg : this.args) {
            rstr += arg.toString(Level + 2);
        }
        rstr += getTabs(Level + 1) + "}\n";
        return rstr;
    }

    @Override
    public AussomType evalImpl(Environment env, boolean getref) throws aussomException {
        return new AussomNull();
    }
}
