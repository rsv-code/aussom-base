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
 * Implemented by an extern backing class that holds bulk memory, so a
 * footprint measurement can count it.
 *
 * <p>Without this, an extern object contributes only the bytes of the
 * Aussom object wrapping it, whatever it is holding behind that. A
 * module whose objects hold arrays, buffers or decoded documents should
 * implement this so a host can see them.
 *
 * <p><b>This is accounting, not enforcement.</b> The number is the
 * extern's own claim about itself and a hostile implementation can lie.
 * That is not a weakness to design around: an extern is arbitrary Java
 * running in the engine's process and can already allocate whatever it
 * likes without asking anyone. The interface exists so an honest module
 * can be measured, and nothing should be built on top of it that assumes
 * the answer is trustworthy.
 *
 * <p>Implementations should be a field read rather than a walk. The
 * method is called during Engine.measureRetainedFootprint, which runs
 * on an engine that is paused or stopped, and a slow answer extends the
 * pause.
 *
 * <p>See AussomFootprint for the size model and
 * design/security-evaluation-g1-g3.md.
 *
 * @author austin
 */
public interface AussomFootprintInt {
	/**
	 * Bytes this object retains, beyond the object itself.
	 * @return A long with the retained bytes, or 0 when it holds none.
	 */
	public long getRetainedBytes();
}
