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

import com.aussom.Engine;
import com.aussom.Environment;
import com.aussom.ast.astClass;
import com.aussom.types.*;
import org.apache.commons.codec.binary.Base64;

public class ABase64 {
    public static String encode(byte[] data)
	{
		return new String(AHex.encode(Base64.encodeBase64(data)));
	}

	public static String encodeRaw(byte[] data)
	{
		return new String(Base64.encodeBase64(data));
	}

	public static byte[] decode(String str) throws Exception
	{
		return Base64.decodeBase64(AHex.decode(str));
	}

	public static byte[] decodeRaw(String str)
	{
		return Base64.decodeBase64(str.getBytes());
	}

	/* Cali Functions */
	public AussomType encode(Environment env, ArrayList<AussomType> args)
	{
		AussomObject obj = (AussomObject)args.get(0);
		if((obj.getExternObject() != null)&&(obj.getExternObject() instanceof ABuffer))
		{
			ABuffer cb = (ABuffer)obj.getExternObject();
			return new AussomString(ABase64.encode(cb.buff));
		}
		else
			return new AussomException("base64.encode(): External object is null or not of type Buffer.");
	}

	public AussomType encodeRaw(Environment env, ArrayList<AussomType> args)
	{
		AussomObject obj = (AussomObject)args.get(0);
		if((obj.getExternObject() != null)&&(obj.getExternObject() instanceof ABuffer))
		{
			ABuffer cb = (ABuffer)obj.getExternObject();
			return new AussomString(ABase64.encodeRaw(cb.buff));
		}
		else
			return new AussomException("base64.encodeRaw(): External object is null or not of type Buffer.");
	}

	public AussomType decode(Environment env, ArrayList<AussomType> args)
	{
		String data = ((AussomString)args.get(0)).getValueString();
		Engine eng = env.getEngine();
		if(eng.getClasses().containsKey("Buffer"))
		{
			astClass cls = eng.getClassByName("Buffer");
			try
			{
				AussomList bargs = new AussomList();
				args.add(new AussomInt(1024));
				AussomObject tb = (AussomObject) cls.instantiate(env, false, bargs);
				ABuffer ab = (ABuffer)tb.getExternObject();
				ab.buff = ABase64.decode(data);
				return tb;
			}
			catch (Exception e)
			{
				return new AussomException("base64.decode(): Class 'Buffer'.");
			}
		}
		else
			return new AussomException("base64.decode(): Class 'Buffer' not found.");
	}

	public AussomType decodeRaw(Environment env, ArrayList<AussomType> args)
	{
		String data = ((AussomString)args.get(0)).getValueString();
		Engine eng = env.getEngine();
		if(eng.getClasses().containsKey("Buffer"))
		{
			astClass cls = eng.getClassByName("Buffer");
			try
			{
				AussomList bargs = new AussomList();
				args.add(new AussomInt(1024));
				AussomObject tb = (AussomObject) cls.instantiate(env, false, bargs);
				ABuffer ab = (ABuffer)tb.getExternObject();
				ab.buff = ABase64.decodeRaw(data);
				return tb;
			}
			catch (Exception e)
			{
				return new AussomException("base64.decodeRaw(): Class 'Buffer'.");
			}
		}
		else
			return new AussomException("base64.decodeRaw(): Class 'Buffer' not found.");
	}
}
