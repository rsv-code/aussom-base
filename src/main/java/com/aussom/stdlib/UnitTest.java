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

import java.util.LinkedHashSet;
import java.util.Set;

public class UnitTest {
    protected String name = "";
    protected String functionName = "";
    protected boolean skip = false;
    protected Set<String> tags = new LinkedHashSet<String>();

    // Per-test timeout in milliseconds. 0 means no timeout. The base
    // runner does not enforce this; the value is captured here so a
    // downstream runner (the aussom CLI) can drive a watchdog. See
    // design/aunit-upgrade-eval.md (M8b).
    protected long timeoutMs = 0;

    public UnitTest() { }
    public UnitTest(String functionName) {
        this.functionName = functionName;
    }
    public UnitTest(String name, String functionName) {
        this.name = name;
        this.functionName = functionName;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getFunctionName() {
        return functionName;
    }
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
    public boolean getSkip() {
        return skip;
    }
    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public Set<String> getTags() {
        return tags;
    }
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
    public void addTag(String tag) {
        if (tag != null && !tag.equals("")) this.tags.add(tag);
    }
    public boolean hasTag(String tag) {
        return this.tags.contains(tag);
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getTestDisplayString() {
        String ret = "*** " + this.functionName + " : ";
        if (this.name != null &&  !this.name.equals("")) {
            ret += this.name + " ... ";
        } else {
            ret += "unit test ... ";
        }
        return ret;
    }
}
