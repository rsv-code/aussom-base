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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.aussom.Util;
import com.aussom.types.*;
import com.aussom.Environment;

public class ADate extends Date implements AussomTypeObjectInt, AussomTypeInt {
	private static final long serialVersionUID = 1579993228939943395L;
	
	public ADate() { }
	
	public static LocalDate dateToLocalDate(Date Dt) {
		return Dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}
	
	public static Date localDateToDate(LocalDate Ld) {
		return new Date(Ld.toEpochDay());
	}
	
	public AussomType newDate(Environment env, ArrayList<AussomType> args) {
		if(!args.get(0).isNull()) {
			long mills = ((AussomInt)args.get(0)).getValue();
			if (mills < 0) {
				mills = (new Date()).getTime();
			}
			this.setTime(mills);
		}
		return env.getClassInstance();
	}
	
	@SuppressWarnings("deprecation")
	public AussomType getHours(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.getHours());
	}
	
	@SuppressWarnings("deprecation")
	public AussomType getMinutes(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.getMinutes());
	}
	
	@SuppressWarnings("deprecation")
	public AussomType getSeconds(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.getSeconds());
	}
	
	public AussomType getTime(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.getTime());
	}
	
	@SuppressWarnings("deprecation")
	public AussomType setHours(Environment env, ArrayList<AussomType> args) {
		this.setHours((int)((AussomInt)args.get(0)).getValue());
		return env.getClassInstance();
	}
	
	@SuppressWarnings("deprecation")
	public AussomType setMinutes(Environment env, ArrayList<AussomType> args) {
		this.setMinutes((int)((AussomInt)args.get(0)).getValue());
		return env.getClassInstance();
	}
	
	@SuppressWarnings("deprecation")
	public AussomType setSeconds(Environment env, ArrayList<AussomType> args) {
		this.setSeconds((int)((AussomInt)args.get(0)).getValue());
		return env.getClassInstance();
	}
	
	public AussomType setTime(Environment env, ArrayList<AussomType> args) {
		this.setTime(((AussomInt)args.get(0)).getValue());
		return env.getClassInstance();
	}
	
	public AussomType toString(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.toString());
	}
	
	public AussomType parse(Environment env, ArrayList<AussomType> args) {
		SimpleDateFormat sdf = new SimpleDateFormat(((AussomString)args.get(1)).getValueString());
		try {
			Date td = sdf.parse(((AussomString)args.get(0)).getValueString());
			this.setTime(td.getTime());
			return env.getClassInstance();
		} catch (ParseException e) {
			return new AussomException("Date.parse(): Parse exception. (" + e.getMessage() + ")");
		}
	}
	
	public AussomType format(Environment env, ArrayList<AussomType> args) {
		SimpleDateFormat sdf = new SimpleDateFormat(((AussomString)args.get(0)).getValueString());
		return new AussomString(sdf.format(this));
	}

	public AussomType isEpoch(Environment env, ArrayList<AussomType> args) {
		if(this.getTime() == 0) {
			return new AussomBool(true);
		}
		return new AussomBool(false);
	}
	
	/*
	 * Helper functions
	 */
	public static Date addDays(Date dt, int numDays) {
		Calendar c = Calendar.getInstance();
		c.setTime(dt);
		c.add(Calendar.DATE, numDays);
		return c.getTime();
	}

	@Override
	public AussomType toJson(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.toString());
	}

	@Override
	public AussomType pack(Environment env, ArrayList<AussomType> args) {
		ArrayList<String> parts = new ArrayList<String>();
		parts.add("\"type\":\"Date\"");
		parts.add("\"value\":\"" + this.toString() + "\"");
		return new AussomString("{" + Util.join(parts, ",") + "}");
	}

	@Override
	public String toString() {
		return this.toString(0);
	}

	@Override
	public String toString(int Level) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf.format(this);
	}

	@Override
	public String str() {
		return this.toString();
	}

	public String str(int Level) {
		return "\"" + this.toString() + "\"";
	}
}