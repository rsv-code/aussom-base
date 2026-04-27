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
import java.util.List;

import com.aussom.Environment;
import com.aussom.ast.*;
import com.aussom.types.AussomBool;
import com.aussom.types.AussomList;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomObject;
import com.aussom.types.AussomString;
import com.aussom.types.AussomType;
import com.aussom.types.AussomTypeInt;
import com.aussom.types.cType;

public class AClass {
	private astClass classDef = null;
	
	public AClass() { }
	
	public void setClass(astClass ClassDef) { this.classDef = ClassDef; }
	
	public AussomType getName(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.classDef.getName());
	}
	
	public AussomType isStatic(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.classDef.getStatic());
	}
	
	public AussomType isExtern(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.classDef.getExtern());
	}
	
	public AussomType getExternClassName(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.classDef.getExternClassName());
	}
	
	public AussomType getMembers(Environment env, ArrayList<AussomType> args) {
		AussomMap mp = new AussomMap();
		
		for(String mname : this.classDef.getMembers().keySet()) {
			astNode memberDef =  this.classDef.getMembers().get(mname);

			AussomMap memberDefMap = new AussomMap();
			memberDefMap.put("type", new AussomString(memberDef.getType().name()));

			// Set annotations
			AussomList alist = new AussomList();
			List<astAnnotation> annotationList = memberDef.getAnnotations();
			for  (astAnnotation annotation : annotationList) {
				alist.getValue().add(annotation.getAussomType());
			}
			memberDefMap.put("annotations", alist);

			mp.put(mname, memberDefMap);
		}
		
		return mp;
	}
	
	public AussomType getMethods(Environment env, ArrayList<AussomType> args) throws aussomException {
		// Returns map[name -> list of overload records]. Each record
		// describes one declared overload (signature, args,
		// annotations, extern flag). With overloading by signature
		// a single name can map to multiple records.
		AussomMap map = new AussomMap();

		for (astFunctDef afd : this.classDef.getAllFunctions()) {
			String mname = afd.getName();
			AussomMap fm = new AussomMap();
			fm.put("isExtern", new AussomBool(afd.getExtern()));
			fm.put("signature", new AussomString(afd.getSignature()));

			// Set annotations
			AussomList annlist = new AussomList();
			List<astAnnotation> annotationList = afd.getAnnotations();
			for (astAnnotation annotation : annotationList) {
				annlist.getValue().add(annotation.getAussomType());
			}
			fm.put("annotations", annlist);

			AussomList alist = new AussomList();
			for (astNode tn : afd.getArgList().getArgs()) {
				AussomMap am = new AussomMap();

				if (tn.getType() == astNodeType.ETCETERA) {
					am.put("name", new AussomString("..."));
				} else {
					am.put("name", new AussomString(tn.getName()));

					String primType = "";
					if (tn.getPrimType() != cType.cUndef) {
						primType = tn.getPrimType().name().toLowerCase().substring(1);
					}
					am.put("requiredType", new AussomString(primType));

					if (tn.getType() == astNodeType.VAR) {
						am.put("hasDefaultValue", new AussomBool(false));
					} else {
						am.put("hasDefaultValue", new AussomBool(true));
						AussomType defVal = tn.eval(env);
						am.put("defaultValueType", new AussomString(defVal.getType().name().toLowerCase().substring(1)));
						am.put("defaultValue", new AussomString(((AussomTypeInt) defVal).str()));
					}
				}

				alist.add(am);
			}
			fm.put("arguments", alist);

			if (map.contains(mname)) {
				((AussomList) map.getValue().get(mname)).add(fm);
			} else {
				AussomList overloads = new AussomList();
				overloads.add(fm);
				map.put(mname, overloads);
			}
		}

		return map;
	}
}