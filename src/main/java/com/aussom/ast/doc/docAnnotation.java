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

package com.aussom.ast.doc;

/**
 * Annotations within the text document.
 */
public class docAnnotation extends docText {
    /**
     * This is the annotation tag name after the '@' symbol.
     */
    private String tagName = "";

    /**
     * The value part after the tag name.
     */
    private String value = "";

    /**
     * The description is any text after the value.
     */
    private String description = "";

    public docAnnotation() {
        this.setType(docType.ANNOTATION);
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCombinedValue() {
        String ret = this.value;
        if (this.description != null && !this.description.equals("")) {
            ret += " " + description;
        }
        return ret;
    }
}
