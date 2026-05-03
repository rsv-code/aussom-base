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

import com.aussom.Environment;
import com.aussom.types.AussomException;
import com.aussom.types.AussomList;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ARegex {
    public static class matchResult
	{
		public String group = "";
		public int start = 0;
		public int end = 0;
	}

	public ARegex() { }

	public static ArrayList<matchResult> match(String RegexStr, String Haystack) throws Exception
	{
		ArrayList<matchResult> res = new ArrayList<matchResult>();

		try
		{
			Pattern pat = Pattern.compile(RegexStr);
			Matcher matcher = pat.matcher(Haystack);
			while (matcher.find())
			{
				matchResult mr = new ARegex.matchResult();
				mr.group = matcher.group();
				mr.start = matcher.start();
				mr.end = matcher.end();
				res.add(mr);
			}
		}
		catch(PatternSyntaxException e)
		{
			throw new Exception(e.getMessage());
		}

		return res;
	}

	public static String replace(String RegexStr, String ReplaceStr, String Haystack) throws Exception
	{
		try
		{
			Pattern pat = Pattern.compile(RegexStr);
			Matcher matcher = pat.matcher(Haystack);
			return matcher.replaceAll(ReplaceStr);
		}
		catch(PatternSyntaxException e)
		{
			throw new Exception(e.getMessage());
		}
	}

	public static String replaceFirst(String RegexStr, String ReplaceStr, String Haystack) throws Exception
	{
		try
		{
			Pattern pat = Pattern.compile(RegexStr);
			Matcher matcher = pat.matcher(Haystack);
			return matcher.replaceFirst(ReplaceStr);
		}
		catch(PatternSyntaxException e)
		{
			throw new Exception(e.getMessage());
		}
	}

	/*
	 * Cali functions
	 */
	public static AussomType match(Environment env, ArrayList<AussomType> args)
	{
		AussomList al = new AussomList();

		String regexStr = ((AussomString)args.get(0)).getValueString();
		String haystack = ((AussomString)args.get(1)).getValueString();

		try
		{
			ArrayList<matchResult> mchs = ARegex.match(regexStr, haystack);
			for(matchResult mr : mchs)
				al.add(new AussomString(mr.group));
		}
		catch (Exception e)
		{
			return new AussomException(e.getMessage());
		}

		return al;
	}

	public static AussomType replace(Environment env, ArrayList<AussomType> args)
	{
		String regexStr = ((AussomString)args.get(0)).getValueString();
		String replaceStr = ((AussomString)args.get(1)).getValueString();
		String haystack = ((AussomString)args.get(2)).getValueString();

		try
		{
			return new AussomString(ARegex.replace(regexStr, replaceStr, haystack));
		}
		catch (Exception e)
		{
			return new AussomException(e.getMessage());
		}
	}

	public static AussomType replaceFirst(Environment env, ArrayList<AussomType> args)
	{
		String regexStr = ((AussomString)args.get(0)).getValueString();
		String replaceStr = ((AussomString)args.get(1)).getValueString();
		String haystack = ((AussomString)args.get(2)).getValueString();

		try
		{
			return new AussomString(ARegex.replaceFirst(regexStr, replaceStr, haystack));
		}
		catch (Exception e)
		{
			return new AussomException(e.getMessage());
		}
	}
}
