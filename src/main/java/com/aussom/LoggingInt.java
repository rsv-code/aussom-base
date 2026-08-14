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

/**
 * This interface provides the functions required to implement a logger.
 * Install one on the engine whose output you want with
 * {@code engine.setLogger(loggingImpl)}. Output is a property of the
 * engine that produced it, not of the thread that ran it, so two
 * engines in one JVM never cross-route.
 */
public interface LoggingInt {
    public void log(String Str);
    public void trc(String Str);
    public void dbg(String Str);
    public void info(String Str);
    public void warn(String Str);
    public void err(String Str);

    public void print(String Text);
    public void println(String Text);
}
