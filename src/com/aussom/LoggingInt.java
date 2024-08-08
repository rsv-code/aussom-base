/*
 * Copyright 2017 Austin Lehman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aussom;

import com.aussom.stdlib.console;

/**
 * This interface provides the functions required
 * to implement a logger that can be registered with
 * the console. In order to register an interface
 * implementation call console.get().register(loggingImpl).
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
