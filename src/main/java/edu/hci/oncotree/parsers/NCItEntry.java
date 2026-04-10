package edu.hci.oncotree.parsers;

import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.hci.oncotree.misc.Util;

public class NCItEntry {

	//fields
	private String code = null;
	private String name = null;
	private String definition = null;
	private HashSet<String> synonyms = new HashSet<String>(); 
	private HashSet<String> altDefinitions = new HashSet<String>();
	
	
	public NCItEntry(JSONObject jo) {
		code = jo.getString("code");
		name = jo.getString("name");
		String nameLowerCase = name.toLowerCase().trim();
		//add synonyms, these can differ just by a single word like 'the' or word order, hmm.
		JSONArray ja = jo.getJSONArray("synonyms");
		for (int i=0; i<ja.length(); i++) {
			JSONObject s = ja.getJSONObject(i);
			String sn = s.getString("name").trim();
			String snLowerCase = sn.toLowerCase();
			if (snLowerCase.equals(nameLowerCase) == false) synonyms.add(sn);
		}
		//add definitions
		if (jo.has("definitions")) {
			ja = jo.getJSONArray("definitions");
			for (int i=0; i<ja.length(); i++) {
				JSONObject s = ja.getJSONObject(i);
				String type = s.getString("type").toUpperCase();
				if (type.equals("DEFINITION")) definition = s.getString("definition").trim();
				else altDefinitions.add(s.getString("definition").trim());
			}
		}
		else {
			Util.pl("No def "+code+" "+name);
		}
		
	}
	
	public String toString(String prepend) {
		StringBuilder sb = new StringBuilder();
		sb.append(prepend); sb.append("NCIt Code : "+code); sb.append("\n");
		sb.append(prepend); sb.append("NCIt Name : "+name); sb.append("\n");
		sb.append(prepend); sb.append("NCIt Definition : "+definition); sb.append("\n");
		for (String s: synonyms) {
			sb.append(prepend); sb.append("NCIt Synonym : "+s); sb.append("\n");
		}
		for (String s: altDefinitions) {
			sb.append(prepend); sb.append("NCIt Alt Definition : "+s); sb.append("\n");
		}
		return sb.toString();
	}
	
	public JSONObject fetchJson() {
		JSONObject jo = new JSONObject();
		jo.put("ncit_code", code);
		jo.put("ncit_name", name);
		jo.put("ncit_definition", definition);
		JSONArray sa = new JSONArray();
		for (String s: synonyms) sa.put(s);
		jo.put("ncit_synonyms", sa);
		JSONArray da = new JSONArray();
		for (String s: altDefinitions) da.put(s);
		jo.put("ncit_alt_definitions", da);
		return jo;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Code:\t"+code);
		sb.append("\n\tName:\t"+name);
		sb.append("\n\tDefinition:\t"+definition);
		for (String s: synonyms) sb.append("\n\tSynonym:\t"+s);
		for (String s: altDefinitions) sb.append("\n\tDefinition:\t"+s);
		return sb.toString();
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}
	
	public String getDefinition() {
		return definition;
	}

	public HashSet<String> getSynonyms() {
		return synonyms;
	}

	public HashSet<String> getAltDefinitions() {
		return altDefinitions;
	}
	
}
