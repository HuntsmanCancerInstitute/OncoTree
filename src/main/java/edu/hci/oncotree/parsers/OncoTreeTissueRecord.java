package edu.hci.oncotree.parsers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import edu.hci.oncotree.misc.Util;

public class OncoTreeTissueRecord {
	
	//fields
	private String tissueCode = null;
	private String tissueName = null;
	private String[] keyPhrases = null;
	private String keyWords = null;
	private String[] descriptions = null;
	private String parentCode = null;
	private String childrenCodes = null;
	
	public OncoTreeTissueRecord (String tissueCode, String tissueName, TreeSet<String> phrases, TreeSet<String> descrips) {
		this.tissueCode = tissueCode;
		this.tissueName = tissueName;
		keyPhrases = Util.treeSetToStringArray(phrases);
		descriptions = Util.treeSetToStringArray(descrips);
		keyWords = collapseToWords(phrases);
	}
	
	public OncoTreeTissueRecord(String code, TreeSet<String> phrases, TreeSet<String> descrips,
			String parentCode, ArrayList<OncoTreeNode> children) {
		this.tissueCode = code;
		keyPhrases = Util.treeSetToStringArray(phrases);
		descriptions = Util.treeSetToStringArray(descrips);
		this.parentCode = parentCode;
		if (children !=null && children.size()!=0) {
			StringBuilder sb = new StringBuilder();
			sb.append(children.get(0).getCode());
			for (int i=1; i< children.size(); i++) {
				sb.append("; ");
				sb.append(children.get(i).getCode());
			}
			childrenCodes = sb.toString();
		}
		
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("oncotree_code : "+tissueCode);
		sb.append("\ntissue_name : "+tissueName);
		sb.append("\nkey_phrases : "+Util.stringArrayToString(keyPhrases, "; "));
		//sb.append("\nkey_words : "+keyWords);
		sb.append("\ndescriptions : "+Util.stringArrayToString(descriptions, "; "));
		return sb.toString();
	}
	
	public String toStringNode() {
		StringBuilder sb = new StringBuilder();
		sb.append("oncotree_code : "+tissueCode);
		sb.append("\nparent_oncotree_code : "+parentCode);
		if (childrenCodes !=null) sb.append("\nchildren_oncotree_codes : "+childrenCodes);
		else sb.append("\nchildren_oncotree_codes : ");
		sb.append("\nkey_phrases : "+Util.stringArrayToString(keyPhrases, "; "));
		//sb.append("\nkey_words : "+keyWords);
		sb.append("\ndescriptions : "+Util.stringArrayToString(descriptions, "; "));
		return sb.toString();
	}
	
	private static Pattern space = Pattern.compile(" ");
	private static String collapseToWords(TreeSet<String> ts) {
		Iterator<String> it = ts.iterator();
		HashSet<String> keys = new HashSet<String>();
		StringBuilder sb = new StringBuilder();
		
		while (it.hasNext() ) {
			String[] words = space.split(it.next());
			for (String s: words) {
				if (keys.contains(s)==false) {
					keys.add(s);
					sb.append(s);
					sb.append(" ");
				}
			}
		}
		return sb.toString();
	}
	
	
}
