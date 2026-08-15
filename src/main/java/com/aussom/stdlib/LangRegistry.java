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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.aussom.Engine;
import com.aussom.Util;

/**
 * The set of standard library modules one {@link Engine} can include,
 * as a name to source map.
 *
 * <p>This is an instance, not a singleton. Each engine owns one, so two
 * engines in the same JVM can be given different standard libraries --
 * a sandboxed engine can be handed a smaller set than a trusted one,
 * and a server can give each application only the modules it is
 * entitled to. The previous design kept one process-wide map that every
 * engine shared, which made that impossible and meant a late
 * registration silently changed what an already-running engine could
 * include. See {@code design/multitenancy-safety.md} section 7.4.
 *
 * @author austin
 */
public class LangRegistry {

	/**
	 * The base standard library, always present. These are the module
	 * names an Aussom program reaches with {@code include}.
	 */
	private static final String[] BASE_MODULES = {
		"lang.aus", "sys.aus", "reflect.aus", "aunit.aus",
		"math.aus", "util.aus", "concurrent.aus"
	};

	/**
	 * Module name to Aussom source.
	 */
	private final Map<String, String> modules = new ConcurrentHashMap<String, String>();

	/**
	 * This JAR file, used to enumerate packaged resource include paths.
	 */
	private File jarFile = null;

	/**
	 * Builds a registry holding the base standard library.
	 */
	public LangRegistry() {
		this.resolveJarFile();
		for (String name : BASE_MODULES) {
			this.modules.put(name, Util.loadResource("/com/aussom/stdlib/aus/" + name));
		}
	}

	/**
	 * Copy constructor. The copy starts with the same modules as the
	 * original and is independent of it afterwards, so handing one
	 * engine a registry derived from another cannot let either change
	 * what the other sees.
	 * @param Other is the LangRegistry to copy.
	 */
	public LangRegistry(LangRegistry Other) {
		this.jarFile = Other.jarFile;
		this.modules.putAll(Other.modules);
	}

	/**
	 * Adds or replaces a module. Embedders use this to register their
	 * own standard library on the engines they build.
	 * @param Name is the include name, for example {@code "http.aus"}.
	 * @param Source is the Aussom source for the module.
	 */
	public void put(String Name, String Source) {
		this.modules.put(Name, Source);
	}

	/**
	 * Checks whether a module with the provided name is registered.
	 * @param Name is the include name to look for.
	 * @return A boolean with true for registered and false for not.
	 */
	public boolean contains(String Name) {
		return this.modules.containsKey(Name);
	}

	/**
	 * Gets the source for a module.
	 * @param Name is the include name to get.
	 * @return A String with the Aussom source, or null when not registered.
	 */
	public String get(String Name) {
		return this.modules.get(Name);
	}

	/**
	 * Gets the live module map for this registry.
	 * @return A Map of module name to Aussom source.
	 */
	public Map<String, String> getModules() {
		return this.modules;
	}

	/**
	 * Lists the resource entries under the provided path, whether the
	 * interpreter is running from a JAR or from a build directory.
	 *
	 * <p>Reports failure by throwing rather than by logging. The caller
	 * is an Engine, which owns a logger and can attribute the message.
	 *
	 * @param Path is the resource path to enumerate.
	 * @return A List of Strings with the entries found.
	 * @throws IOException on failure to read the JAR.
	 * @throws URISyntaxException on a malformed resource URL.
	 */
	public List<String> listResourceDirectory(String Path) throws IOException, URISyntaxException {
		List<String> ret = new ArrayList<String>();
		if (this.jarFile != null && this.jarFile.isFile()) {
			JarFile jar = new JarFile(this.jarFile);
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				// Make sure name starts with '/'.
				if (!name.startsWith("/")) name = "/" + name;
				if (name.startsWith(Path)) {
					ret.add(name);
				}
			}
			jar.close();
		} else {
			URL url = Engine.class.getResource(Path);
			if (url != null) {
				File entries = new File(url.toURI());
				ret = this.getFileResources(entries);
			}
		}

		return ret;
	}

	private List<String> getFileResources(File path) {
		List<String> ret = new ArrayList<String>();
		File[] listing = path.listFiles();
		if (listing != null) {
			for (File entry : listing) {
				if (entry.isDirectory()) {
					List<String> tret = this.getFileResources(entry);
					ret.addAll(tret);
				} else if (entry.getPath().endsWith(".aus")) {
					ret.add(entry.getPath());
				}
			}
		}
		return ret;
	}

	/**
	 * Locates the JAR this class was loaded from. A failure here is not
	 * fatal: listResourceDirectory falls back to the directory-based
	 * lookup when jarFile is null, which is the case when running from
	 * a build directory anyway.
	 */
	private void resolveJarFile() {
		String jarFileUrl = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		try {
			this.jarFile = new File(URLDecoder.decode(jarFileUrl, StandardCharsets.UTF_8.name()));
		} catch (UnsupportedEncodingException e) {
			this.jarFile = null;
		}
	}
}
