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

import com.aussom.TestSecurityManagerImpl;

/**
 * Test security manager that lets a test choose its own limits.
 *
 * There is no Java-side setter on SecurityManagerImpl and no limit setter
 * on Engine, on purpose: limits are policy, and a host expresses policy
 * by building the security manager it wants and handing it to the Engine
 * constructor. Reaching the property map is what subclassing is for,
 * which is exactly what a host does too.
 *
 * Used by CallDepth, RegexBudget, ResourceLimits and EngineControl so
 * every one of them sets a limit the way a host would.
 */
public class LimitSecMan extends TestSecurityManagerImpl {

	/**
	 * Sets a property, chaining so an engine can be built in one
	 * expression.
	 * @param Key is the property name.
	 * @param Value is the value to store.
	 * @return This manager.
	 */
	public LimitSecMan with(String Key, Object Value) {
		this.props.put(Key, Value);
		return this;
	}

	/**
	 * Sets a numeric property.
	 * @param Key is the property name.
	 * @param Value is the value to store.
	 * @return This manager.
	 */
	public LimitSecMan with(String Key, long Value) {
		this.props.put(Key, Long.valueOf(Value));
		return this;
	}

	/**
	 * Removes a property, so a test can exercise what happens when a key
	 * is absent.
	 * @param Key is the property name.
	 * @return This manager.
	 */
	public LimitSecMan without(String Key) {
		this.props.remove(Key);
		return this;
	}
}
