package edu.hci.oncotree.parsers;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import edu.hci.oncotree.misc.Util;

public class OncoTreeNode {

	private String code = null;
	private String name = null;
	private String mainType = null;
	private String tissue = null;
	private String parentCode = null;
	private String[] umlsReferences = null;
	private String[] ncitReferences = null;
	private NCItEntry[] ncitEntries = null;
	private ArrayList<OncoTreeNode> children = null; 
	private OncoTreeParser oncoTreeParser = null;
	private OncoTreeNode[] branch = null;
	
	public String toStringAll(String prepend) {
		StringBuilder sb = new StringBuilder();
		sb.append(prepend); sb.append("OncoTree Code : "); sb.append(code); sb.append("\n");
		sb.append(prepend); sb.append("OncoTree Name : "); sb.append(name); sb.append("\n");
		sb.append(prepend); sb.append("OncoTree MainType : "); sb.append(mainType); sb.append("\n");
		sb.append(prepend); sb.append("OncoTree Tissue : "); sb.append(tissue); sb.append("\n");
		sb.append(prepend); sb.append("OncoTree Parent Code : "); 
		if (parentCode!=null) sb.append(parentCode); 
		else sb.append("None");
		sb.append("\n");
		sb.append(prepend); sb.append("OncoTree Children Codes : "); sb.append(fetchChildrenNodeCodes()); sb.append("\n");
		if (ncitEntries != null) {
			for (NCItEntry ne: ncitEntries) sb.append(ne.toString(prepend));
		}
		return sb.toString();
	}
	
	public JSONObject fetchJson() {
		JSONObject jo = new JSONObject();
		jo.put("oncotree_code", code);
		jo.put("oncotree_name", name);
		jo.put("oncotree_main_type", mainType);
		jo.put("oncotree_tissue", tissue);
		if (parentCode !=null) jo.put("oncotree_parent_code", parentCode);
		else jo.put("oncotree_parent_code", "None");
		
		JSONArray c = new JSONArray();
		if (children != null) for (OncoTreeNode n: children) c.put(n.getCode());
		jo.put("oncotree_children_codes", c);
		
		JSONArray b = new JSONArray();
		fetchOrderedBranch();
		if (branch != null) for (OncoTreeNode n: branch) b.put(n.getCode());
		jo.put("oncotree_branch_codes", b);
		
		JSONArray n = new JSONArray();
		if (ncitEntries != null) for (NCItEntry e: ncitEntries) n.put(e.fetchJson());
		jo.put("ncit_entries", n);
		return jo;
	}
	
	public String fetchChildrenNodeCodes() {
		if (children==null) return "None";
		StringBuilder sb = new StringBuilder(children.get(0).getCode());
		for (int i=1; i< children.size(); i++) {
			sb.append(",");
			sb.append(children.get(i).getCode());
		}
		return sb.toString();
	}

	public OncoTreeNode(JSONObject ob, OncoTreeParser oncoTreeParser) {
		code = ob.getString("code");
		name = ob.getString("name");
		//watch out for Tissue, many null values
		if (name.equals("Tissue")) return;
		mainType = ob.getString("mainType");
		tissue = ob.getString("tissue");
		parentCode = ob.getString("parent");
		code = ob.getString("code");
		JSONObject ref = ob.getJSONObject("externalReferences");
		//any UMLs
		if (ref.has("UMLS")) {
			JSONArray ja = ref.getJSONArray("UMLS");
			umlsReferences = new String[ja.length()];
			for (int i=0; i< umlsReferences.length; i++) umlsReferences[i] = ja.getString(i);
		}
		//any NCI
		if (ref.has("NCI")) {
			JSONArray ja = ref.getJSONArray("NCI");
			ncitReferences = new String[ja.length()];
			for (int i=0; i< ncitReferences.length; i++) ncitReferences[i] = ja.getString(i);
		}
			
		this.oncoTreeParser = oncoTreeParser;
	}
	
	
	public String getNameCode() {
		StringBuilder sb = new StringBuilder(name);
		sb.append(" (");
		sb.append(code);
		sb.append(")");
		return sb.toString();
	}
	public String getCode() {
		return code;
	}
	public String getName() {
		return name;
	}
	public String getMainType() {
		return mainType;
	}
	public String getTissue() {
		return tissue;
	}
	public String getParentCode() {
		return parentCode;
	}
	public String[] getUmlsReferences() {
		return umlsReferences;
	}
	public String[] getNcitReferences() {
		return ncitReferences;
	}
	public ArrayList<OncoTreeNode> getChildren() {
		return children;
	}
	public void setChildren(ArrayList<OncoTreeNode> children) throws Exception {
		if (this.children != null) throw new Exception("Children have already been set for "+code);
		this.children = children;
		//for each child, fetch code and set it's children, this will recurse through the whole tree
		for (OncoTreeNode n : children) {
			String code = n.getCode();
			ArrayList<OncoTreeNode> c = oncoTreeParser.fetchNodesWithParentCode(code);
			if (c.size() !=0) n.setChildren(c);
		}
	}
	public OncoTreeNode[] fetchOrderedBranch() {
		//already created?
		if (branch != null) return branch;
		
		//build it
		ArrayList<OncoTreeNode> nodes = new ArrayList<OncoTreeNode>();
		
		//this will recurse through all nodes
		addParentNode(nodes);
		
		//flip order
		branch = new OncoTreeNode[nodes.size()];
		int counter = 0;
		for (int i=branch.length-1; i>=0; i--) branch[i] = nodes.get(counter++);
		return branch;
	}
	private void addParentNode(ArrayList<OncoTreeNode> nodes) {
		if (code.equals("TISSUE")==false) nodes.add(this);
		// does this have a parent
		if (parentCode == null || parentCode.length() == 0) return;
		OncoTreeNode parentNode = oncoTreeParser.getCodeNodes().get(parentCode);
		parentNode.addParentNode(nodes);
	}

	public void setNcitEntries(NCItEntry[] entries) {
		this.ncitEntries = entries;
		
	}

	public NCItEntry[] getNcitEntries() {
		return ncitEntries;
	}
}