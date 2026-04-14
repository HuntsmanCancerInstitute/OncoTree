package edu.hci.oncotree.apps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.hci.oncotree.misc.Util;
import edu.hci.oncotree.parsers.NCIThesaurusParser;
import edu.hci.oncotree.parsers.NCItEntry;
import edu.hci.oncotree.parsers.OncoTreeNode;
import edu.hci.oncotree.parsers.OncoTreeParser;
import edu.hci.oncotree.parsers.OncoTreeTissueRecord;

public class OncoTreePrinter {

	//user fields
	private File oncoTreeJsonFile = null;
	private File nciThesaurusDir = null;
	private File saveDir = null;
	private boolean addNciSynonyms = true;
	private boolean verbose = false;
	
	//internal fields
	private OncoTreeParser otParser = null;
	private NCIThesaurusParser ncitParser = null;
	private TreeMap<String, ArrayList<OncoTreeNode[]>> tissueNameBranch = null;
	private TreeMap<String, ArrayList<OncoTreeNode>>  tissueNameNodes = null;
	private TreeMap<String, String> tissueNameCode = null;
	
	public OncoTreePrinter (String[] args) {
		try {
			processArgs(args);
			
			//Parse OncoTree model
			otParser = new OncoTreeParser (verbose, oncoTreeJsonFile);

			//Parse NCI Thesaurus entries
			ncitParser = new NCIThesaurusParser(verbose, nciThesaurusDir);

			// For every OncoTree node link in the NCI thesaurus info, if it exists, 233 nodes don't have an entry
			otParser.linkNCIiEntries(ncitParser);
			
			//group by the tissue, this populates the children, parents, and branches, needed for all of the exports
			tissueNameBranch = new TreeMap<String, ArrayList<OncoTreeNode[]>>();
			tissueNameCode = new TreeMap<String, String>();
			for (OncoTreeNode leaf: otParser.getLeavesWithBranches()) {
				//this starts with the tissue node
				OncoTreeNode[] branchNodes = leaf.fetchOrderedBranch();
				String tissueName = branchNodes[0].getName();
				String tissueCode = branchNodes[0].getCode();
				tissueNameCode.put(tissueName, tissueCode);
				ArrayList<OncoTreeNode[]> al = tissueNameBranch.get(tissueName);
				if (al==null) {
					al = new ArrayList<OncoTreeNode[]>();
					tissueNameBranch.put(tissueName, al);
				}
				al.add(branchNodes);
			}
			
			//save tissues and the branches associated with them as txt, not that useful?
			//saveTissueAndBranches();
			
			//save all of the nodes as json with full information to a single file
			//saveNodes();
			
			//save each tissue and it's associated nodes as individual files
			tissueNameNodes = otParser.getTissueNodes();
			
			//saveCondensedTissuesAndNodes();
			//saveTissueJsonl();
			
			saveCondensedNodeInfoTxt();
			saveCondensedTissuesInfoTxt();
			
		} catch (Exception e) {
			e.printStackTrace();
			Util.printErrAndExit("\nERROR running the OncoTreePrinter\n");
		}
	}

	private TreeSet<String> removeShortDuplicates(TreeSet<String> ts){
		String[] strings = new String[ts.size()];
		Iterator<String> it = ts.iterator();
		int index = 0;
		while (it.hasNext() ) strings[index++] = it.next();
		TreeSet<String> toReturn = new TreeSet<String>();
		//for each string
		for (int i=0; i< strings.length; i++) {
			String test = strings[i].trim();
			int numHits = 0;
			//check all others
			for (int j=0; j< strings.length; j++) {
				if (strings[j].contains(test)) numHits++;
			}
			if (numHits == 1) toReturn.add(test);
		}
		return toReturn;
	}
	
	private void saveCondensedTissuesInfoTxt() throws Exception {
		
		Util.pl("Condensing "+tissueNameNodes.size()+" tissue node info for tissue matching...");
		PrintWriter out = new PrintWriter (new FileWriter(new File (saveDir, "condensedTissueInfo.txt")));
		
		//for each tissue
		for (String tissueCode: tissueNameNodes.keySet()) {
			
			String clean = tissueCode.replace(" ", "_");
			clean = clean.replace("/", "_");

			//oncotree_name, oncotree_main_type, ncit_name, ncit_synonyms - remove ', NOS'
			TreeSet<String> keyPhrases = new TreeSet<String>();

			//oncotree_tissue, should be only one
			TreeSet<String> tissues = new TreeSet<String>();

			//ncit_definitions
			TreeSet<String> definitions = new TreeSet<String>();

			//for each associated node
			for (OncoTreeNode n: tissueNameNodes.get(tissueCode)) {
				tissues.add(n.getTissue());
				keyPhrases.add(clean(n.getName()));
				keyPhrases.add(clean(n.getMainType()));
				if (n.getNcitEntries()!=null) {
					for (NCItEntry ne: n.getNcitEntries()) {
						keyPhrases.add(clean(ne.getName()));
						if (ne.getDefinition()!=null) definitions.add(ne.getDefinition());
						if (addNciSynonyms) {
							for (String syn: ne.getSynonyms()) keyPhrases.add(clean(syn));
						}
					}
				}
			}
			//just one tissue?
			if (tissues.size()!=1) throw new Exception("ERROR: not just one tissue: "+tissues);
			//clean up the keyPhrases
			keyPhrases = removeShortDuplicates(keyPhrases);
			//Create a TissueRecord
			OncoTreeTissueRecord tr = new OncoTreeTissueRecord(tissueNameCode.get(tissueCode), tissues.first(), keyPhrases, definitions);
			//Util.pl(tr+"\n");
			out.println(tr.toString());
			out.println();

		}
		out.close();
	}

	private static Pattern multiSpace = Pattern.compile(" {2,}");
	private static String clean(String word) {
		//replace any multiple spaces with just one
		Matcher m = multiSpace.matcher(word);
		String clean = m.replaceAll(" ");
		//remove any ', NOS'
		clean = clean.replace(", NOS", " ");
		//lower case
		clean = clean.toLowerCase();
		//remove ,
		clean = clean.replace(",", "");
		//remove of
		clean = clean.replace(" of", "");
		//remove -
		clean = clean.replace(" -", "");
		//remove the
		clean = clean.replace(" the", "");
		//remove any leading or trailing white space
		clean = clean.trim();
		return clean;
	}
	private void saveCondensedNodeInfoTxt() throws Exception {
		Util.pl("\nSaving "+tissueNameNodes.size()+" simplified tissue txt files...");

		File tissueDir = new File (saveDir, "TissueNodeCatalog");
		tissueDir.mkdir();
		
		PrintWriter outCodes = new PrintWriter (new FileWriter(new File (tissueDir, "tissueCodeNodeCodes.txt")));
		
		//for each tissue
		for (String tissueName: tissueNameNodes.keySet()) {
			String tissueCode = tissueNameCode.get(tissueName);
			outCodes.print(tissueCode+" : ");
			
			PrintWriter out = new PrintWriter (new FileWriter(new File (tissueDir, tissueCode+".txt")));

			//for each node in the tissue
			for (OncoTreeNode n: tissueNameNodes.get(tissueName)) {
				outCodes.print(n.getCode()+", ");
				
				//oncotree_name, oncotree_main_type, ncit_name, ncit_synonyms - remove ', NOS'
				TreeSet<String> keyPhrases = new TreeSet<String>();

				//oncotree_tissue, should be only one
				TreeSet<String> tissues = new TreeSet<String>();

				//ncit_definitions
				TreeSet<String> definitions = new TreeSet<String>();
				
				tissues.add(n.getTissue());
				keyPhrases.add(n.getName());
				keyPhrases.add(n.getMainType());
				if (n.getNcitEntries()!=null) {
					for (NCItEntry ne: n.getNcitEntries()) {
						keyPhrases.add(ne.getName());
						if (ne.getDefinition()!=null) definitions.add(ne.getDefinition());
						if (addNciSynonyms) {
							for (String syn: ne.getSynonyms()) keyPhrases.add(syn);
						}
					}
				};
				//just one tissue?
				if (tissues.size()!=1) throw new Exception("ERROR: not just one tissue: "+tissues);
				//clean up the keyPhrases
				keyPhrases = removeShortDuplicates(keyPhrases);
				//Create a TissueRecord
				OncoTreeTissueRecord tr = new OncoTreeTissueRecord(n.getCode(), keyPhrases, definitions, n.getParentCode(), n.getChildren());
				//Util.pl("\n"+tr.toStringNode());
				out.println(tr.toStringNode());
				out.println();
			}
			outCodes.println();
			out.close();
		}
		outCodes.close();
	}
	private void saveNodes() throws IOException {
		Util.pl("Saving "+otParser.getCodeNodes().size()+" nodes as JSON and JSONL...");
		PrintWriter outJson = new PrintWriter (new FileWriter(new File (saveDir, "nodes.json")));
		PrintWriter outJsonL = new PrintWriter (new FileWriter(new File (saveDir, "nodes.jsonl")));
		for (OncoTreeNode n: otParser.getCodeNodes().values()) {
			JSONObject jo = n.fetchJson();
			outJson.println(jo.toString(3));
			outJsonL.println(jo.toString());
		}
		outJson.close();
		outJsonL.close();
	}
	private void saveTissueAndBranches() throws IOException {
		Util.pl("Saving "+tissueNameBranch.size()+" tissues and branches...");
		PrintWriter out = new PrintWriter (new FileWriter(new File (saveDir, "tissuesAndBranches.txt")));
		for (String tissueName: tissueNameBranch.keySet()) {
			StringBuilder sb = new StringBuilder(tissueName);
			sb.append("\n");

			//for each branch
			for (OncoTreeNode[] b : tissueNameBranch.get(tissueName)) {
				sb.append("\t");
				sb.append(b[0].getNameCode());
				for (int i=1; i< b.length; i++) {
					sb.append(" -> ");
					sb.append(b[i].getNameCode());
				}
				sb.append("\n");
			}
			out.println(sb);
		}
		out.close();
	}
	private void saveTissueJsonl() throws IOException {
		Util.pl("Saving tissue as jsonl...");
		PrintWriter out = new PrintWriter (new FileWriter(new File (saveDir, "tissues.jsonl")));
		for (String tissueName: tissueNameNodes.keySet()) {
			Util.pl("\t"+tissueName);
			JSONArray ja = new JSONArray();
			ja.put(tissueNameNodes.get(tissueName));
			out.println(ja.toString());
		}
		out.close();
	}
	public static void main(String[] args) {
		if (args.length ==0){
			printDocs();
			System.exit(0);
		}
		new OncoTreePrinter(args);
	}		

	/**This method will process each argument and assign new variables
	 * @throws FileNotFoundException */
	public void processArgs(String[] args) throws FileNotFoundException{
		Pattern pat = Pattern.compile("-[a-z]");
		System.out.println("\nOncoTreePrinter Arguments: "+Util.stringArrayToString(args, " ")+"\n");
		for (int i = 0; i<args.length; i++){
			String lcArg = args[i].toLowerCase();
			Matcher mat = pat.matcher(lcArg);
			if (mat.matches()){
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 'o': oncoTreeJsonFile = new File(args[++i]); break;
					case 'n': nciThesaurusDir = new File(args[++i]); break;
					case 's': saveDir = new File(args[++i]); break;
					case 'v': verbose = true; break;
					case 'x': addNciSynonyms = false; break;
					default: Util.printErrAndExit("\nProblem, unknown option! " + mat.group());
					}
				}
				catch (Exception e){
					Util.printErrAndExit("\nSorry, something doesn't look right with this parameter: -"+test+"\n");
				}
			}
		}
		if (oncoTreeJsonFile == null || oncoTreeJsonFile.exists()== false) Util.printErrAndExit("\nERROR: cannot find your OncoTree json file, "+oncoTreeJsonFile);
		if (nciThesaurusDir == null || nciThesaurusDir.exists()== false) Util.printErrAndExit("\nERROR: cannot find your NCI Thesaurus directory, "+nciThesaurusDir);
		if (saveDir == null) Util.printErrAndExit("\nERROR: cannot find the directory to save the results, "+saveDir);
		saveDir.mkdirs();
	}	
	
	public static void printDocs(){
		System.out.println("\n" +
				"**************************************************************************************\n" +
				"**                             OncoTree Printer : April 2026                        **\n" +
				"**************************************************************************************\n" +
				"OTP parses the OncoTree data structure and the associated NCI Thesaurus terms to\n" +
				"generate flat files approriate for LLM prompts.\n"+

				"\nRequired Options:\n"+
				"\n-o Path to a json file representing the OncoTree data structure, e.g. curl -o otl.json\n"+
				"      'https://oncotree.info/api/tumorTypes?version=oncotree_latest_stable' \n"+
				"-r Path to a directory containing json files for each NCI Thesaurs concept referenced\n"+
				"      in the oncoTree.json, e.g. curl -o C5545.json\n"+
				"      'https://api-evsrest.nci.nih.gov/api/v1/concept/ncit/C5545?include=full'\n"+
				"-s Path to a directory to save the results.\n"+
				"-x Don't add NCI Thesaurus synonyms to key_phrases\n"+
				"-v Verbose output.\n"+
				
				"\nExample: java -Xmx1G -jar ~/OTApps/OncoTreePrinterXXX.jar -o otl.json -r NCIt/ -s\n"+
				"              ParsedOTFiles\n"+

				"**************************************************************************************\n");
	}

	

}
