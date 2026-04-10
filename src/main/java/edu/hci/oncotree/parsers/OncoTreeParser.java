package edu.hci.oncotree.parsers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;
import edu.hci.oncotree.misc.Util;

/**
 * Get the latest, e.g. curl -o oncotreeModelLatest.json "https://oncotree.info/api/tumorTypes?version=oncotree_latest_stable"
 * */
public class OncoTreeParser {
	
	//fields
	private File oncoTreeJson = null;
	private HashMap<String, OncoTreeNode> codeNodes = null;
	private TreeMap<String, ArrayList<OncoTreeNode>> tissueNodes = null;
	private ArrayList<OncoTreeNode> leavesWithBranches = null;
	private boolean verbose = true;
	
	//constructors
	public OncoTreeParser (boolean verbose, File oncoTreeJson) throws Exception {
		this.verbose = verbose;
		this.oncoTreeJson = oncoTreeJson;
		parseJson();
		addChildren();
		buildBranches();
	}

	private void printBranchesToScreen() {
		for (OncoTreeNode l: leavesWithBranches) {
			OncoTreeNode[] branch = l.fetchOrderedBranch();
			Util.pl("Leaf: "+l.getNameCode());
			//print
			StringBuilder sb = new StringBuilder("\t");
			for (OncoTreeNode n: branch) {
				Util.pl(sb+ n.getNameCode());
				sb.append("\t");
			}
		}
	}

	private void buildBranches() {
		//find all of the nodes with no children, these are the last node in the branch, a terminal leaf
		leavesWithBranches = new ArrayList<OncoTreeNode>();
		for (OncoTreeNode otn: codeNodes.values()) {
			if (otn.getChildren() == null && otn.getCode().equals("TISSUE")==false) {
				leavesWithBranches.add(otn);
				// this will assign itself to the leaf
				otn.fetchOrderedBranch();
			}	
		}
		if (verbose) Util.pl("Built "+leavesWithBranches.size()+" branches with a terminal leaf");
	}
	
	public TreeMap<String, ArrayList<OncoTreeNode>> getTissueNodes() {
		if (tissueNodes != null) return tissueNodes;
		
		//find all of the nodes with parent as TISSUE, these are the first node in the branch
		tissueNodes = new TreeMap<String, ArrayList<OncoTreeNode>>();
		for (OncoTreeNode otn: codeNodes.values()) {
			String parent = otn.getParentCode();
			if (parent != null && parent.equals("TISSUE")) {
				ArrayList<OncoTreeNode> al = new ArrayList<OncoTreeNode>();
				al.add(otn);
				tissueNodes.put(otn.getName(), al);
			}	
		}
		
		//walk the other nodes and add them to the appropriate tissue
		for (OncoTreeNode otn: codeNodes.values()) {
			String parent = otn.getParentCode();
			if (parent != null && parent.equals("TISSUE") == false) {
				ArrayList<OncoTreeNode> al = tissueNodes.get(otn.getTissue());
				al.add(otn);
			}
		}
		return tissueNodes;
	}

	private void addChildren() throws Exception {
		//find all of the nodes with "parent": "TISSUE", these are the root node for all of the tissues
		ArrayList<OncoTreeNode> tissues = new ArrayList<OncoTreeNode>();
		for (OncoTreeNode otn: codeNodes.values()) {
			String parentCode = otn.getParentCode();
			if (parentCode != null && parentCode.equals("TISSUE")) tissues.add(otn);	
		}
		if (verbose) Util.pl("Parsed "+tissues.size()+" tissues");
		
		//for each tissue, find all of the nodes that list it as the parent and make those children, 
		//  this will then trigger a recursive search for all the children and populate the branches
		for (OncoTreeNode t: tissues) {
			String code = t.getCode();
			ArrayList<OncoTreeNode> children = fetchNodesWithParentCode(code);
			t.setChildren(children);
		}
	}

	ArrayList<OncoTreeNode> fetchNodesWithParentCode(String code) {
		ArrayList<OncoTreeNode> al = new ArrayList<OncoTreeNode>();
		for (OncoTreeNode n: this.codeNodes.values()) {
			String parent = n.getParentCode();
			if (parent != null && parent.equals(code)) al.add(n);
		}
		return al;
	}

	private void parseJson() {
		String jString = Util.loadFile(oncoTreeJson, " ", true);
		JSONArray ja = new JSONArray(jString);
		int num = ja.length();
		codeNodes = new HashMap<String, OncoTreeNode>();
		for (int i=0; i< num; i++) {
			OncoTreeNode otn = new OncoTreeNode(ja.getJSONObject(i), this);
			codeNodes.put(otn.getCode(), otn);
		}
		if (verbose) Util.pl("Parsed "+codeNodes.size()+" nodes");
	}

	public static void main(String[] args) throws Exception {
		OncoTreeParser otp = new OncoTreeParser (false, new File ("/Users/u0028003/HCI/Hackathons/2025/OncoTreeMapper/oncoTreeTumorTypePretty.json"));
		otp.printBranchesToScreen();
	}

	public HashMap<String, OncoTreeNode> getCodeNodes() {
		return codeNodes;
	}

	public ArrayList<OncoTreeNode> getLeavesWithBranches() {
		return leavesWithBranches;
	}

	public void linkNCIiEntries(NCIThesaurusParser ntp) throws IOException {
		HashMap<String, NCItEntry> codeEntry = ntp.getCodeEntry();
		
		//Add NCI data to OncoTreeNodes
		for (OncoTreeNode n : codeNodes.values()) {
			String[] ncitCodes = n.getNcitReferences();
			if (ncitCodes != null) {
				NCItEntry[] entries = new NCItEntry[ncitCodes.length];
				for (int i=0; i< ncitCodes.length; i++) {
					entries[i] = codeEntry.get(ncitCodes[i]);
					if ((entries[i]) == null) throw new IOException("ERROR: failed to find the NCIt code "+ncitCodes[i]+" from the NCIT json files.");
				}
				n.setNcitEntries(entries);
			}
		}
		
	}

}
