package edu.hci.oncotree.apps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.hci.oncotree.misc.Util;
import edu.hci.oncotree.parsers.Call;
import edu.hci.oncotree.parsers.CallParser;
import edu.hci.oncotree.parsers.OncoTreeNode;
import edu.hci.oncotree.parsers.OncoTreeParser;
import edu.hci.oncotree.parsers.TissueCall;

public class OncoTreeComparator {

	//user fields
	private File keyTestIdCalls = null;
	private File oncoTreeJsonFile = null;
	private File callDir = null;
	private boolean ignoreNoneCalls = false;
	
	//internal fields
	private OncoTreeParser otParser = null;
	private HashMap<String, OncoTreeNode> otParserCodeNode = null;
	private HashMap<String, String> testIdOTCodeKey = null;
	private TreeMap<String, String> tissueNameCode = null;
	private HashSet<String> tissueCodes = new HashSet<String>();
	private boolean verbose = false;
	
	public OncoTreeComparator (String[] args) {
		try {
			processArgs(args);
			
			//Parse OncoTree model, this will contain the node code and tissue
			Util.pl("Parsing OncoTree...");
			otParser = new OncoTreeParser (true, oncoTreeJsonFile);
			otParserCodeNode = otParser.getCodeNodes();
			
			tissueNameCode = new TreeMap<String, String>();
			for (OncoTreeNode leaf: otParser.getLeavesWithBranches()) {
				//this starts with the tissue node
				OncoTreeNode[] branchNodes = leaf.fetchOrderedBranch();
				String tissueName = branchNodes[0].getName();
				String tissueCode = branchNodes[0].getCode();
				tissueNameCode.put(tissueName, tissueCode);
				tissueCodes.add(tissueCode);
			}
			tissueCodes.add("NONE");
				
			//Parse the key
			parseKey();
			
			//Process each call set
			//parseCalls();
			parseCallsTissue();
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
			Util.printErrAndExit("\nERROR running the OncoTreeComparator\n");
		}

	}
	private void parseCalls() throws Exception {
		Util.pl("\nComparing calls...");
		Util.pl("\nDataset\tTumorsClassified\tKeyTestIdsFound\tTissueMatches\tCodeMatches");
		for (File jl: Util.extractFiles(callDir, ".jsonl")) {
			ArrayList<String> res = new ArrayList<String>();
			String callSetName = jl.getName().substring(0, jl.getName().length()-6);
			res.add(callSetName);
			CallParser cp = new CallParser(jl);
			res.add(new Integer(cp.getTestIdCall().size()).toString());
			int[] numTissCodeIdsMatches = scoreCalls(cp, false);
			for (Integer i: numTissCodeIdsMatches) res.add(new Integer(i).toString());
			Util.pl(Util.stringArrayListToString(res, "\t"));
		}
	}
	
	private void parseCallsTissue() throws Exception {
		Util.pl("\nComparing tissue calls...");

		Util.pl("\nDataset\tTumorsClassified\tKeyTestIdsFound\tTissueMatches");

		//for each directory
		for (File dir: Util.extractOnlyDirectories(callDir)) {
			
			HashMap<String, TissueCall> callSetNameTissueCall = new HashMap<String, TissueCall>();
			//for each json file in the dir
			for (File j: Util.extractFiles(dir, ".json")) {
				//tissue_granite4_01IH7HG27S
				String fullName = j.getName().substring(0, j.getName().length()-5);
				TissueCall tc = new TissueCall(loadLlmJsonResponseFile(j), tissueCodes, verbose, j);
				callSetNameTissueCall.put(fullName, tc);
			}

			int[] numTissueMatchesNumIds = scoreTissueCalls(callSetNameTissueCall, verbose);
			Util.pl(dir.getName()+"\t"+callSetNameTissueCall.size()+"\t"+numTissueMatchesNumIds[1]+"\t"+numTissueMatchesNumIds[0]);
		}
	}
	
	private int[] scoreTissueCalls(HashMap<String, TissueCall> nameCall, boolean verbose) throws IOException {
		int numTissueMatches = 0;
		int numTestIdFound = 0;

		//for each call
		for (TissueCall tc: nameCall.values()) {

			//is the testId from the key in the call set?
			if (testIdOTCodeKey.containsKey(tc.getTestOrderId()) == false) {
				Util.el("\tWARNING: failed to find key testId "+tc.getTestOrderId()+" in call set, skipping!");
			}

			else {
				//get calls
				String testCall = tc.getOncoTreeCode();
				
				String finalKeyCode = testIdOTCodeKey.get(tc.getTestOrderId());
				String keyCall = null;
				if (finalKeyCode.equals("NONE")) keyCall = "NONE";
				else {
					OncoTreeNode otn = otParserCodeNode.get(finalKeyCode);
					keyCall = tissueNameCode.get(otn.getTissue());
				}
				
				if (verbose) 

				//skip NONEs?
				if (ignoreNoneCalls && testCall.equals("NONE") ) continue;
				numTestIdFound++;

				//score for the tissue match and for the final match
				boolean tissueMatch = false;

				if (testCall.equals(keyCall)) {
					numTissueMatches++;
					tissueMatch = true;
				}

				if (verbose && tissueMatch==false) {
					Util.pl("\tMismatch for "+tc.getTestOrderId()+" testCall "+testCall+" keyCall "+keyCall);
					Util.pl("\t\tConfidence: "+tc.getConfidence()+" Reasoning: "+tc.getReasoning());
				}
			}
		}
		return new int[] {numTestIdFound, numTissueMatches};
	}
	
	
	private int[] scoreCalls(CallParser cp, boolean verbose) throws IOException {
		int numTissueMatches = 0;
		int numCodeMatches = 0;
		int numTestIdFound = 0;

		HashMap<String, Call> testIdCall = cp.getTestIdCall();

		//for each key
		for (String testId: testIdOTCodeKey.keySet()) {

			//is the testId from the key in the call set?
			if (testIdCall.containsKey(testId) == false) {
				//Util.el("\tWARNING: failed to find key testId "+testId+" in call set, skipping");
			}

			else {
				
				//get code and tissueCode for the prediction call
				Call call = testIdCall.get(testId);
				String oncoTreeCodeCall = call.getOncoTreeCode();
				String oncoTreeTissueCodeCall = null;
				if (oncoTreeCodeCall.equals("NONE") == false) {
					OncoTreeNode node = otParserCodeNode.get(oncoTreeCodeCall);
					if (node == null) throw new IOException("ERROR: failed to find the node for the call "+oncoTreeCodeCall);
					String tissueName = node.getTissue();
					oncoTreeTissueCodeCall = fetchTissueCode(tissueName);
				}
				else oncoTreeTissueCodeCall = "NONE";
				
				//skip NONEs?
				if (ignoreNoneCalls && oncoTreeCodeCall.equals("NONE") ) continue;
				
				numTestIdFound++;

				//get code and tissueCode for the key
				String oncoTreeCodeKey = testIdOTCodeKey.get(testId);
				String oncoTreeTissueCodeKey = null;
				if (oncoTreeCodeKey.equals("NONE") == false) {
					OncoTreeNode node = otParserCodeNode.get(oncoTreeCodeKey);
					if (node == null) throw new IOException("ERROR: failed to find the node for the key "+oncoTreeCodeKey);
					String tissueName = node.getTissue();
					oncoTreeTissueCodeKey = fetchTissueCode(tissueName);
				}
				else oncoTreeTissueCodeKey = "NONE";
				if (verbose) Util.pl("\tLooking for "+oncoTreeCodeKey+" and "+oncoTreeTissueCodeKey+ " in "+testId);



				if (verbose) Util.pl("\tLooking at "+oncoTreeCodeCall+" and "+oncoTreeTissueCodeCall+ " in "+testId);
				
				//score for the tissue match and for the final match
				boolean tissueMatch = false;
				boolean codeMatch = false;
				if (oncoTreeTissueCodeKey.equals(oncoTreeTissueCodeCall)) {
					numTissueMatches++;
					tissueMatch = true;
				}
				if (oncoTreeCodeKey.equals(oncoTreeCodeCall)) {
					numCodeMatches++;
					codeMatch = true;
				}
				if (verbose) Util.pl("\t\tTissue "+tissueMatch+", Code "+codeMatch+"\n");
				if (tissueMatch==false) {
					Util.pl("\tFailed tissueMatch for "+testId+" it predicted -> "+oncoTreeTissueCodeCall+ " when it's -> "+oncoTreeTissueCodeKey);
				}
			}
		}
		return new int[] {numTestIdFound, numTissueMatches, numCodeMatches};
	}
	
	private String fetchTissueCode( String tissueName) throws IOException {
		String tc = this.tissueNameCode.get(tissueName);
		if (tc == null) throw new IOException ("ERROR: failed to find the tissue code from this tissue name "+tissueName);
		return tc;
	}
	private void parseKey() {
		Util.pl("\nParsing key...");
		testIdOTCodeKey = parseKeyFileAllCapsValue(keyTestIdCalls);
		Util.pl("\tNumber keys "+testIdOTCodeKey.size()+" : "+testIdOTCodeKey);
	}
	
	/**Parses a tab delimited file, the indexed column is used as the key, 
	 * the entire line as the value, blank lines skipped, returns null if 
	 * a duplicate key is found.*/
	public static HashMap<String,String> parseKeyFileAllCapsValue (File file){
		HashMap<String,String> al = new HashMap<String,String>();
		String line = null;
		try{
			BufferedReader in = Util.fetchBufferedReader(file);
			String[] tokens;
			while ((line=in.readLine()) != null){
				line = line.trim();
				if (line.length() == 0 || line.startsWith("#")) continue;
				tokens = line.split("\t");
				if (tokens.length!=2) {
					Util.pl("\tSkipping split not == 2 tokens: "+line);
					continue;
				}
				if (al.containsKey(tokens[0])) Util.pl("\tDuplicate: "+line);
				al.put(tokens[0], tokens[1].toUpperCase());
			}
			in.close();
		} catch (Exception e){
			e.printStackTrace();
			System.err.println("\nError: problem parsing file to hash. Line -> "+line);
			return null;
		}
		return al;
	}
	
	/**Loads a file's lines into a String, skips lines that are empty or starts with ```  , gz/zip OK.*/
	public static String loadLlmJsonResponseFile(File file){
		StringBuilder sb = new StringBuilder();
		try{
			BufferedReader in = Util.fetchBufferedReader(file);
			String line;
			boolean endFound = false;
			while ((line = in.readLine())!=null && endFound==false){
				//advance until ^{ found
				line = line.trim();
				if (line.length()==0) continue;
				if (line.equals("{")) {
					sb.append(line);
					sb.append("\n");
					while ((line = in.readLine())!=null) {
						line = line.trim();
						sb.append(line);
						sb.append("\n");
						if (line.equals("}")) {
							endFound = true;
							break;
						}	
					}
				}
			}
			in.close();
			if (endFound == false) throw new Exception ("Failed to find a closing }.");
		}catch(Exception e){
			e.printStackTrace();
			Util.printErrAndExit("Prob loading "+file);
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		if (args.length ==0){
			printDocs();
			System.exit(0);
		}
		new OncoTreeComparator(args);
	}		

	/**This method will process each argument and assign new variables
	 * @throws FileNotFoundException */
	public void processArgs(String[] args) throws FileNotFoundException{
		Pattern pat = Pattern.compile("-[a-z]");
		System.out.println("\nOncoTreeComparator Arguments: "+Util.stringArrayToString(args, " ")+"\n");
		for (int i = 0; i<args.length; i++){
			String lcArg = args[i].toLowerCase();
			Matcher mat = pat.matcher(lcArg);
			if (mat.matches()){
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 'o': oncoTreeJsonFile = new File(args[++i]); break;
					case 'k': keyTestIdCalls = new File(args[++i]); break;
					case 'c': callDir = new File(args[++i]); break;
					case 'n': ignoreNoneCalls = true; break;
					case 'v': verbose = true; break;
					default: Util.printErrAndExit("\nProblem, unknown option! " + mat.group());
					}
				}
				catch (Exception e){
					Util.printErrAndExit("\nSorry, something doesn't look right with this parameter: -"+test+"\n");
				}
			}
		}
		if (oncoTreeJsonFile == null || oncoTreeJsonFile.exists()== false) Util.printErrAndExit("\nERROR: cannot find your OncoTree json file, "+oncoTreeJsonFile);
		if (keyTestIdCalls == null || keyTestIdCalls.exists()== false) Util.printErrAndExit("\nERROR: cannot find your key, "+keyTestIdCalls);
		if (callDir == null) Util.printErrAndExit("\nERROR: cannot find the directory to save the results, "+callDir);
	}	
	
	public static void printDocs(){
		System.out.println("\n" +
				"**************************************************************************************\n" +
				"**                            OncoTree Comparator : Jan 2026                        **\n" +
				"**************************************************************************************\n" +
				"Use this tool to compare a key of TestIDs and their OncoTree classification codes with\n" +
				"one or more LLM call sets. Note the tissue name for each call is pulled from the node\n"+
				"info not the tissue name in the jsonl file. There shouldn't be a difference.\n\n" +

				"Required Options:\n"+
				"-o Path to a json file representing the OncoTree data structure, e.g. curl -o otl.json\n"+
				"      'https://oncotree.info/api/tumorTypes?version=oncotree_latest_stable' \n"+
				"-k Path to a file containing two tab delimited columns, the test_order_id and the\n"+
				"      oncotree_code\n"+
				"-c Path to a directory containing jsonl files, one per LMM call set to compare with\n"+
				"      the key\n"+
				"-n Ignore 'NONE' calls\n"+
				"-v Verbose debugging output\n"+
				
				"\nExample: java -Xmx1G -jar ~/OTApps/OncoTreeComparator.jar -o otl.json -r key.txt\n"+
				"   -d LlmCalls/ \n"+

				"**************************************************************************************\n");
	}

	

}
