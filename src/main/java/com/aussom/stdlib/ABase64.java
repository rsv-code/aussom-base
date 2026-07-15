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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.aussom.Engine;
import com.aussom.Environment;
import com.aussom.ast.astClass;
import com.aussom.types.*;
import org.apache.commons.codec.binary.Base64;

public class ABase64 {
    // Bytes -> safe encoding (hex of base64): output is only 0-9 and a-f.
	public static String encodeSafe(byte[] data)
	{
		return new String(AHex.encode(Base64.encodeBase64(data)));
	}

	// Bytes -> standard base64.
	public static String encodeBinary(byte[] data)
	{
		return new String(Base64.encodeBase64(data));
	}

	// Safe encoding (hex of base64) -> bytes.
	public static byte[] decodeSafe(String str) throws Exception
	{
		return Base64.decodeBase64(AHex.decode(str));
	}

	// Standard base64 -> bytes.
	public static byte[] decodeBinary(String str)
	{
		return Base64.decodeBase64(str.getBytes());
	}

	/* Cali Functions */

	// encode(string) -> string : standard base64 of the UTF-8 string.
	public AussomType encode(Environment env, ArrayList<AussomType> args)
	{
		String str = ((AussomString)args.get(0)).getValueString();
		return new AussomString(ABase64.encodeBinary(str.getBytes(StandardCharsets.UTF_8)));
	}

	// decode(string) -> string : standard base64 -> UTF-8 string.
	public AussomType decode(Environment env, ArrayList<AussomType> args)
	{
		String data = ((AussomString)args.get(0)).getValueString();
		return new AussomString(new String(ABase64.decodeBinary(data), StandardCharsets.UTF_8));
	}

	// encodeBinary(Buffer) -> string : standard base64 of the buffer bytes.
	public AussomType encodeBinary(Environment env, ArrayList<AussomType> args)
	{
		AussomObject obj = (AussomObject)args.get(0);
		if((obj.getExternObject() != null)&&(obj.getExternObject() instanceof ABuffer))
		{
			ABuffer cb = (ABuffer)obj.getExternObject();
			return new AussomString(ABase64.encodeBinary(cb.buff));
		}
		else
			return new AussomException("base64.encodeBinary(): Argument is null or not of type Buffer.");
	}

	// decodeBinary(string) -> Buffer : standard base64 -> bytes.
	public AussomType decodeBinary(Environment env, ArrayList<AussomType> args)
	{
		String data = ((AussomString)args.get(0)).getValueString();
		Engine eng = env.getEngine();
		if(eng.getClasses().containsKey("Buffer"))
		{
			astClass cls = eng.getClassByName("Buffer");
			try
			{
				AussomList bargs = new AussomList();
				AussomObject tb = (AussomObject) cls.instantiate(env, false, bargs);
				ABuffer ab = (ABuffer)tb.getExternObject();
				ab.buff = ABase64.decodeBinary(data);
				return tb;
			}
			catch (Exception e)
			{
				return new AussomException("base64.decodeBinary(): " + e.getMessage());
			}
		}
		else
			return new AussomException("base64.decodeBinary(): Class 'Buffer' not found.");
	}

	// encodeSafe(string) -> string : URL/filename/everywhere-safe encoding
	// (hex of base64) of the UTF-8 string. The output is limited to the
	// characters 0-9 and a-f, so it needs no escaping anywhere.
	public AussomType encodeSafe(Environment env, ArrayList<AussomType> args)
	{
		String str = ((AussomString)args.get(0)).getValueString();
		return new AussomString(ABase64.encodeSafe(str.getBytes(StandardCharsets.UTF_8)));
	}

	// decodeSafe(string) -> string : safe (hex of base64) -> UTF-8 string.
	public AussomType decodeSafe(Environment env, ArrayList<AussomType> args)
	{
		String data = ((AussomString)args.get(0)).getValueString();
		try
		{
			return new AussomString(new String(ABase64.decodeSafe(data), StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			return new AussomException("base64.decodeSafe(): " + e.getMessage());
		}
	}
}
