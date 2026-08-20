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
import com.aussom.ast.doc.docAnnotation;
import com.aussom.ast.doc.docText;
import com.aussom.ast.doc.docType;
import com.aussom.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class astAussomDoc extends astNode implements astNodeInt {
    // The full doc comment text.
    private String aussomDocText = "";

    // The parsed doc comment broken into a list of text or annotation nodes.
    private List<docText> docList = new ArrayList<docText>();

    // Whether stripFormatting and parseText have run for the current
    // text. See ensureParsed.
    private boolean parsed = false;

    /*
     * Patterns compiled once rather than per line. String.matches,
     * String.replaceFirst and String.split("\\s+") each compile a fresh
     * Pattern on every call, and these run on every line of every doc
     * comment in every file an engine parses. Pattern is immutable and
     * thread safe, so sharing one across engines shares no mutable
     * state; the Matcher is built per call and never shared.
     */
    private static final Pattern LEADING_STARS = Pattern.compile("^[*]+(.*)");
    private static final Pattern ANNOTATION = Pattern.compile("@[A-Za-z0-9_]+.*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");


    public astAussomDoc() {
        this.setType(astNodeType.AUSSOM_DOC);
    }

    public astAussomDoc(String Text) {
        this();
        this.setAussomDocText(Text);
    }

    private void setAussomDocText(String Text) {
        // Set the text. Stripping and parsing are deferred to the first
        // read; see ensureParsed.
        this.aussomDocText = Text;
        this.parsed = false;
    }

    /**
     * Strips the formatting and parses the text, once, on first read.
     *
     * Doc comments are a large share of a typical Aussom source file
     * and nothing on the parse-then-run path reads the parsed form, so
     * doing this work while parsing charged every engine for something
     * most never ask for. Every accessor that exposes the parsed form
     * calls this first, so callers see no difference beyond the first
     * one paying for it.
     *
     * Synchronized because this is public API reached from public
     * getters and callers outside this project run on their own
     * threads. stripFormatting rewrites aussomDocText in place, so two
     * threads arriving together is a data race on that field, not
     * merely duplicated work. It runs at most once per node and never
     * on the parse path, so the monitor costs nothing that matters.
     */
    private synchronized void ensureParsed() {
        if (this.parsed) {
            return;
        }
        this.parsed = true;
        this.stripFormatting();
        this.parseText();
    }

    private void stripFormatting() {
        StringBuilder ret = new StringBuilder();
        String lines[] = this.aussomDocText.split("\n");
        for (String line : lines) {
            String tline = line.trim();
            if (tline.startsWith("*"))
                tline = LEADING_STARS.matcher(tline).replaceFirst("$1").trim();
            if (!tline.equals(""))
                ret.append(tline).append("\n");
        }
        this.aussomDocText = ret.toString().trim();
    }

    private void parseText() {
        List<docText> lst = new ArrayList<docText>();

        String lines[] = this.parseProcessLines();
        for(String line : lines) {
            if (ANNOTATION.matcher(line).matches()) {
                // Annoation found.
                docAnnotation an = new docAnnotation();
                an.setText(line);
                String parts[] = WHITESPACE.split(line);
                an.setTagName(parts[0].substring(1));
                if (parts.length > 1) {
                    an.setValue(parts[1]);
                }
                if (parts.length > 2) {
                    String desc = parts[2];
                    for (int i = 3; i < parts.length; i++) {
                        desc += " " + parts[i];
                    }
                    an.setDescription(desc);
                }
                lst.add(an);
            } else {
                lst.add(new docText(line));
            }
        }

        this.docList = lst;
    }

    /**
     * Processes all the individual lines and groups up plain text and
     * annotation nodes into individual strings.
     * @return An array of Strings with each logical line.
     */
    private String[] parseProcessLines() {
        ArrayList<String> ret = new ArrayList();

        String cur = "";
        String lines[] = this.aussomDocText.split("\n");
        for (String line : lines) {
            if (ANNOTATION.matcher(line).matches()) {
                if (!cur.trim().equals("")) {
                    ret.add(cur.trim());
                    cur = "";
                }
                cur += line + " ";
            } else if (line.trim().equals("")) {
                if (!cur.trim().equals("")) {
                    ret.add(cur.trim());
                    cur = "";
                }
            } else {
                cur += line + " ";
            }
        }

        // If there's any left not added already.
        if (!cur.trim().equals("")) {
            ret.add(cur.trim());
        }

        return ret.toArray(new String[ret.size()]);
    }

    @Override
    public String toString(int Level) {
        this.ensureParsed();
        String rstr = "";
        rstr += getTabs(Level) + "{\n";
        rstr += this.getNodeStr(Level + 1) + ",\n";
        rstr += getTabs(Level + 1) + "\"fileName\": \"" + this.getFileName() + "\",\n";
        rstr += getTabs(Level + 1) + "\"aussomDocText\": \"" + this.aussomDocText + "\",\n";
        return rstr;
    }

    @Override
    public AussomType evalImpl(Environment env, boolean getref) throws aussomException {
        return new AussomNull();
    }

    public AussomType getAussomdoc() {
        this.ensureParsed();
        AussomMap ret = new AussomMap();

        ret.put("aussomDocText", new AussomString(this.aussomDocText));
        AussomList lst = new AussomList();
        for (docText dt : this.docList) {
            AussomMap dobj = new AussomMap();
            dobj.put("type", new AussomString(dt.getType().name()));
            dobj.put("text", new AussomString(dt.getText()));
            if (dt.getType() == docType.ANNOTATION) {
                docAnnotation da = (docAnnotation)dt;
                dobj.put("tagName", new AussomString(da.getTagName()));
                dobj.put("tagValue", new AussomString(da.getValue()));
                dobj.put("tagDescription", new AussomString(da.getDescription()));
            }
            lst.add(dobj);
        }
        ret.put("docList", lst);

        return ret;
    }

    /**
     * Gets a list of annotations for this doc node.
     * @return A List of docAnnotation objects.
     */
    public List<docAnnotation> getDocAnnotations() {
        this.ensureParsed();
        List<docAnnotation> ret = new ArrayList<>();
        for (docText dt : this.docList) {
            if (dt.getType() == docType.ANNOTATION) {
                docAnnotation da = (docAnnotation) dt;
                ret.add(da);
            }
        }
        return ret;
    }
}
