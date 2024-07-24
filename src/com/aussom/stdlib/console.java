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

package com.aussom.stdlib;

import java.util.ArrayList;

import com.aussom.Environment;
import com.aussom.LoggingInt;
import com.aussom.types.AussomNull;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;

public class console {
	private static ThreadLocal<console> _instance =
		new ThreadLocal<console>() {
			@Override
			protected console initialValue() {
				return new console();
			}
		};

	private LoggingInt loggingInt = null;


	public console() {
		this.init();
	}
	
	public void init() {
		// Do any initialization here!
	}
	
	public static console get() {
		return _instance.get();
	}

	/**
	 * Registers a logging interface implementation. If you
	 * want to deregister a logging interface implementation then
	 * pass null as an argument.
	 * @param loggingInt is null or the LoggingInt implementation.
	 */
	public void register(LoggingInt loggingInt) {
		this.loggingInt = loggingInt;
	}
	
	public console log(String Str) {
		if (this.loggingInt != null) {
			this.loggingInt.log(Str);
		} else {
			this.println(Str);
		}
		return this;
	}
	public console info(String Str) {
		if (this.loggingInt != null) {
			this.loggingInt.info(Str);
		} else {
			this.println("[info] " + Str);
		}
		return this;
	}
	public console warn(String Str) {
		if (this.loggingInt != null) {
			this.loggingInt.warn(Str);
		} else {
			this.println("[warn] " + Str);
		}
		return this;
	}
	public console err(String Str) {
		if (this.loggingInt != null) {
			this.loggingInt.err(Str);
		} else {
			this.println("[error] " + Str);
		}
		return this;
	}
	
	public synchronized console print(String Text) {
		if (this.loggingInt != null) {
			this.loggingInt.print(Text);
		} else {
			System.out.print(Text);
			System.out.flush();
		}
		return this;
	}
	
	public console println(String Text) {
		if (this.loggingInt != null) {
			this.loggingInt.println(Text);
		} else {
			this.print(Text + "\n");
		}
		return this;
	}
	
	public AussomType _log(Environment env, ArrayList<AussomType> args) {
		console.get().log(((AussomTypeInt)args.get(0)).str());
		return new AussomNull();
	}
	
	public AussomType _info(Environment env, ArrayList<AussomType> args) {
		console.get().info(((AussomTypeInt)args.get(0)).str());
		return new AussomNull();
	}
	
	public AussomType _warn(Environment env, ArrayList<AussomType> args) {
		console.get().warn(((AussomTypeInt)args.get(0)).str());
		return new AussomNull();
	}
	
	public AussomType _err(Environment env, ArrayList<AussomType> args) {
		console.get().err(((AussomTypeInt)args.get(0)).str());
		return new AussomNull();
	}
	
	public AussomType _print(Environment env, ArrayList<AussomType> args) {
		console.get().print(((AussomTypeInt)args.get(0)).str());
		return new AussomNull();
	}
	
	public AussomType _println(Environment env, ArrayList<AussomType> args) {
		console.get().println(((AussomTypeInt)args.get(0)).str());
		return new AussomNull();
	}
}
