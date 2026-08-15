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

import java.util.ArrayList;

import com.aussom.Environment;
import com.aussom.SecurityManagerImpl;
import com.aussom.types.AussomException;
import com.aussom.types.AussomType;

/**
 * Aussom instance of the security manager extern object. This class
 * provides an implementation of the SecurityManagerInt that's available
 * to be used in Aussom, instantiated and provided to an Engine object.
 *
 * <p>What a script gets from <code>new SecurityManager()</code> is a
 * policy <i>value</i>, not the engine's policy. Writing to it does not
 * change what the running program is allowed to do, because there is no
 * way to install it: an Engine takes its security manager in the
 * constructor and exposes no setter. The static <code>secman</code>
 * class, backed by ASecMan, is the one that reads the engine's own
 * policy.
 *
 * @author Austin Lehman
 */
public class ASecurityManager extends SecurityManagerImpl {

	/**
	 * Default constructor. Deliberately does nothing beyond the parent's
	 * property setup.
	 *
	 * The permission check used to live here, reading
	 * this.getProperty("securitymanager.instantiate"). That could never
	 * fail: "this" is the object being built, and SecurityManagerImpl's
	 * constructor had populated its property map with instantiate = true
	 * a moment earlier, so the running engine's policy was never
	 * consulted. A constructor cannot do better, because it has no
	 * Environment and therefore no way to reach the engine.
	 *
	 * The check is in newSecurityManager() instead, which the Aussom
	 * constructor in lang.aus calls. See F6 in
	 * design/security-evaluation-f6-f10.md.
	 */
	public ASecurityManager() {
		super();
	}

	/**
	 * Aussom SecurityManager(). Gated on securitymanager.instantiate as
	 * read from the engine that is running, which is the point: the old
	 * check read the object's own copy of the property and could not
	 * refuse.
	 *
	 * On success it opens up this object's own get, list and set
	 * permissions, so a script can read and write the policy value it
	 * just created. That is safe because the object is not the engine's
	 * policy; see the class comment.
	 *
	 * @param env is the current Environment object.
	 * @param args is an ArrayList of AussomType objects which are the
	 *             function arguments.
	 * @return This object, or an AussomException when policy refuses.
	 */
	public AussomType newSecurityManager(Environment env, ArrayList<AussomType> args) {
		if (!(Boolean) env.getEngine().getSecurityManager()
				.getProperty("securitymanager.instantiate")) {
			return new AussomException("SecurityManager(): Security exception, action "
				+ "'securitymanager.instantiate' not permitted.");
		}

		// Security manager itself. - Set these to true so that the
		// aussom created security manager can have it's properties
		// set and listed.
		this.props.put("securitymanager.property.get", true);
		this.props.put("securitymanager.property.list", true);
		this.props.put("securitymanager.property.set", true);

		return env.getClassInstance();
	}
}
