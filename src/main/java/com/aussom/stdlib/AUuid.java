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
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.UUID;

public class AUuid {
    private static SecureRandom sran = null;

	private static SecureRandom getSecRandInst() throws Exception
	{
		if(sran == null) {
			try {
				sran = SecureRandom.getInstance("SHA1PRNG");
			} catch (NoSuchAlgorithmException e) {
				throw new Exception(e.getMessage());
			}
		}
		return sran;
	}

	/*
	 * Generate UUID/GUID
	 */
	public static String get()
	{
		return UUID.randomUUID().toString();
	}

	public static String secure() throws Exception
	{
		SecureRandom sran = getSecRandInst();
		String randNum = new Integer(sran.nextInt()).toString();
		MessageDigest sha;
		try
		{
			sha = MessageDigest.getInstance("SHA-1");
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new Exception(e.getMessage());
		}
		byte[] result =  sha.digest(randNum.getBytes());
		return ABase64.encode(result);
	}

	public static AussomType get(Environment env, ArrayList<AussomType> args)
	{
		return new AussomString(AUuid.get());
	}

	public static AussomType getSecure(Environment env, ArrayList<AussomType> args)
	{
		try
		{
			return new AussomString(AUuid.secure());
		}
		catch (Exception e)
		{
			return new AussomException(e.getMessage());
		}
	}
}
