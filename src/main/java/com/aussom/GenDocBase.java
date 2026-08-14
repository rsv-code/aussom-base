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

import java.io.File;

public class GenDocBase {
    /*
     * This is a build-time entry point with no Engine of its own --
     * Main.getAussomdocMarkdown builds and discards one per file -- so
     * it owns a plain logger rather than reaching for a global.
     */
    private static final LoggingInt LOG = new DefaultLoggingImpl();

    public static void main(String[] args) throws Exception {
        // Generates all the docs
        genDoc("src/main/resources/com/aussom/stdlib/aus/aunit.aus", "doc");
        genDoc("src/main/resources/com/aussom/stdlib/aus/lang.aus", "doc");
        genDoc("src/main/resources/com/aussom/stdlib/aus/math.aus", "doc");
        genDoc("src/main/resources/com/aussom/stdlib/aus/reflect.aus", "doc");
        genDoc("src/main/resources/com/aussom/stdlib/aus/sys.aus", "doc");

    }

    private static void genDoc(String inFile, String outDir) throws Exception {
        File ifile = new File(inFile);

        if (ifile.exists()) {
            // Ensure outDir exists
            buildOutDir(outDir);

            String aussomFile = ifile.getName();
            LOG.info("Now to generate doc for file '" + aussomFile + "'.");
            String outFile = aussomFile + ".md";
            if (!outDir.trim().equals(""))
                outFile = outDir + "/" + outFile;
            Util.write(outFile, Main.getAussomdocMarkdown(inFile), false);
            LOG.info("Wrote doc to '" + outFile + "'.");
        } else {
            LOG.err("Provided input file '" + inFile + "' wasn't found.");
        }
    }

    private static void buildOutDir(String dirName) {
        File outDir = new File(dirName);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
    }
}
