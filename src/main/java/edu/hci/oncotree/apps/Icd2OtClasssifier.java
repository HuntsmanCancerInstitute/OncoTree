package edu.hci.oncotree.apps;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.hci.oncotree.misc.Util;
import edu.hci.oncotree.parsers.OncoTreeNode;
import edu.hci.oncotree.parsers.OncoTreeParser;


public class Icd2OtClasssifier {

	//user fields
	private File icdOFile = null;
	private File icd10File = null;
	private File testCodesFile = null;
	private File oncoTreeFile = null;
	private File jsonSaveDir = null;
	
	//internal
	private HashMap<String, String> icdO2OtCodes = null;
	private HashMap<String, String> icd102OtCodes = null;
	private OncoTreeParser oncoTreeParser = null;
	private HashMap<String, OncoTreeNode> codeNodes = null;
	private TreeMap<String, ArrayList<OncoTreeNode>> codeAllNodes = null;
	
	public Icd2OtClasssifier (String[] args) {


		try {
			processArgs(args);
			
			//icd o codes are a combination of two, a C code and an 8 number, e.g. C07.9 8072/3
			//represent a topographic code and the morphology code
			icdO2OtCodes = loadJson(icdOFile);
			
			//icd 10 codes are just a C, e.g. C43.1. represent a disease
			icd102OtCodes = loadJson(icd10File);
			
			//fetch OncoTree info
			oncoTreeParser = new OncoTreeParser(false, oncoTreeFile);
			codeNodes = oncoTreeParser.getCodeNodes();
			codeAllNodes = oncoTreeParser.getTissueNodes();
			
			//walk through the testing icd code info and attempt to match to the hashes
			walkTestInfo();

		} catch (Exception e) {
			Util.el("\nERROR running the Icd2OtClassifier");
			e.printStackTrace();
			System.exit(1);
		}

	}
	

	private void walkTestInfo() {
		//#OTID	tempusIcdOCodeTopography	tempusIcdOCodeMorphology	tempusIcd10Code
		//    0           1        2       3
		//VC3XWWB286	C34.9	8070/3	C34,C34.9
		//Y53XQMQKXU	null	9040/3	C49.9
		//J5I86Q9FNB	C77.9	9673/3	C81-C96,C96.9
		//3GHGXPXSUV	C77.9	722953004	C85.10,C81-C96,C96.9
		String[] lines = Util.loadFile(testCodesFile);
		for (String l: lines) {
			if (l.startsWith("#") || l.trim().length()==0) continue;
			String[] fields = Util.TAB.split(l);
			//Util.pl(l);
			
			//topo morph - oncology specific
			String otCodeFromTopoMorph = null;
			//are either null?
			if (fields[1].equals("null")==false && fields[2].equals("null")==false) {
				String tmCodeFromTest = fields[1]+" "+fields[2];
				otCodeFromTopoMorph = icdO2OtCodes.get(tmCodeFromTest);
				//Util.pl("\tLooking for "+tmCodeFromTest+" -> "+otCodeFromTopoMorph);
			}
			
			//general disease if icd O failed
			HashSet<String> otCodesFromDisease = null;
			
			if (otCodeFromTopoMorph == null && fields[3].equals("null")==false) {
				String[] split = Util.COMMA.split(fields[3]);
				otCodesFromDisease = new HashSet<String>();
				for (String s: split) {
					String otCode = icd102OtCodes.get(s);
					//Util.pl("\tLooking for "+s+" -> "+otCode);
					if (otCode != null) otCodesFromDisease.add(otCode);
				}
			}
			
			//combine them, sometimes they are the same
			HashSet<String> combineOTCodes = new HashSet<String>();
			if (otCodeFromTopoMorph != null) combineOTCodes.add(otCodeFromTopoMorph);
			if (otCodesFromDisease != null && otCodesFromDisease.size() > 0) combineOTCodes.addAll(otCodesFromDisease);
			
			if (combineOTCodes.size() !=0) {
				
				//find tissues
				Util.pl(fields[0]+ "\t"+ combineOTCodes);
				Iterator<String> calledCodes = combineOTCodes.iterator();
				HashSet<String> foundTissues = new HashSet<String>();
				OncoTreeNode otNode = null;
				
				while (calledCodes.hasNext()) {
					String cc = calledCodes.next();
					otNode = codeNodes.get(cc);
					if (otNode == null) {
						Util.el("Failed to find "+cc+" in the OncoTree, tissue "+otNode.getTissue());
						System.exit(1);
					}
					else {
						String tissueDisplayName = otNode.getTissue(); //this is the tissue display name not it's code!
						ArrayList<OncoTreeNode> sortedNodes = codeAllNodes.get(tissueDisplayName);
						if (sortedNodes == null) Util.printErrAndExit("Failed to find sorted nodes for "+cc+ " , tissue "+otNode.getTissue());
						OncoTreeNode tissueNode = sortedNodes.get(0);
						//Util.pl("Found tissue "+ tissueNode.getCode()+ " for "+cc);
						foundTissues.add(tissueNode.getCode());
					}
				}
				
				//more than one tissue? attempt to remove OTHER
				if (foundTissues.size() !=1) foundTissues.remove("OTHER");
				String tissueCode = null;
				if (foundTissues.size() !=1) {
					Util.printErrAndExit("Failed to find just one tissue for "+l+" seeing "+foundTissues);
				}
				else {
					tissueCode = foundTissues.iterator().next();
					Util.pl("\tTissueCode:\t"+ tissueCode);
				}
				
				
				//find tumor node
				String nodeCode = null;
				if (combineOTCodes.size() == 1) {
					nodeCode = otNode.getCode();
					Util.pl("\tNodeCode:\t"+nodeCode);
				}
				else {
					//attempt to remove tissue code
					combineOTCodes.remove(tissueCode);
					if (combineOTCodes.size() == 1) {
						otNode = codeNodes.get(combineOTCodes.iterator().next());
						nodeCode = otNode.getCode();
						Util.pl("\tNodeCode:\t"+nodeCode);
					}
					else {
						//attempt to remove OTHER
						combineOTCodes.remove("OTHER");
						if (combineOTCodes.size() == 1) {
							otNode = codeNodes.get(combineOTCodes.iterator().next());
							nodeCode = otNode.getCode();
							Util.pl("\tNodeCode:\t"+nodeCode);
						}
						else {
							//attempt to remove BNNOS, hard coded fix for the two with both BNNOS and IDC
							combineOTCodes.remove("BNNOS");
							if (combineOTCodes.size() == 1) {
								otNode = codeNodes.get(combineOTCodes.iterator().next());
								nodeCode = otNode.getCode();
								Util.pl("\tNodeCode:\t"+nodeCode);
							}
							else Util.printErrAndExit("\tMULTIPLE NodeCodes:\t"+combineOTCodes);
						}
					}
				}
				//build and save json object
				JSONObject jo = new JSONObject();
				jo.put("sample_id", fields[0]);
				jo.put("model", "ICD OT Classifier");
				jo.put("date_classified", Util.getDate());
				jo.put("oncotree_tissue_code", tissueCode);
				jo.put("tissue_confidence", "NA");
				jo.put("tissue_classification_ok", true);
				jo.put("oncotree_node_code", nodeCode);
				jo.put("node_classification_ok", true);
				jo.put("node_confidence", "NA");
				
				Util.writeString(jo.toString(3), new File(jsonSaveDir, fields[0]+"."+tissueCode +"."+nodeCode+".json"));
			}
		}
	}


	private HashMap<String, String> loadJson(File json) {
		HashMap<String, String> hash = new HashMap<String, String>();
		String jString = Util.loadFile(json, " ", true);
		JSONObject jo = new JSONObject(jString);
		JSONArray ja = jo.getJSONArray("element");
		int num = ja.length();
		for (int i=0; i< num; i++) {
			JSONObject je = ja.getJSONObject(i);
			
			//either "C71.9 9440/3" or just "C09.8"
			String icdCode = je.getString("code").trim();
			
			//already present?
			if (hash.containsKey(icdCode)) Util.printErrAndExit("Duplicate ICD Code found: "+icdCode);

			JSONArray eJa = je.getJSONArray("target");
			int numTarget = eJa.length();
			if (numTarget > 1) Util.printErrAndExit("Multiple targets found for: "+icdCode); 
			for (int j=0; j< numTarget; j++) {
				JSONObject target = eJa.getJSONObject(j);
				String otCode = target.getString("code");
				
				//String description = target.getString("display");
				//Util.pl(icdCode+" -> "+otCode+"\t"+description);
				hash.put(icdCode, otCode);
			}
		}
		//Util.pl(hash);
		return hash;
	}


	public static void main(String[] args) {
		new Icd2OtClasssifier(args);
	}		

	/**This method will process each argument and assign new variables
	 * @throws FileNotFoundException */
	public void processArgs(String[] args) throws FileNotFoundException{
		//any args?
		if (args.length ==0) {
			printDocs();
			System.exit(0);
		}
		System.out.println("\nIcd2OtClassifier Arguments: "+Util.stringArrayToString(args, " ")+"\n");
		Pattern pat = Pattern.compile("-[a-z0-9]");
		for (int i = 0; i<args.length; i++){
			String lcArg = args[i].toLowerCase();
			Matcher mat = pat.matcher(lcArg);
			if (mat.matches()){
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 'o': icdOFile = new File(args[++i]); break;
					case '1': icd10File = new File(args[++i]); break;
					case 't' : testCodesFile = new File(args[++i]); break;
					case 'n' : oncoTreeFile = new File(args[++i]); break;
					case 'j' : jsonSaveDir = new File(args[++i]); break;
					default: 
						Util.el("Problem, unknown option!" + mat.group());
						System.exit(1);
					}
				}
				catch (Exception e){
					Util.el("Sorry, something doesn't look right with this parameter: -"+test);
					System.exit(1);
				}
			}
		}
		jsonSaveDir.mkdirs();

	}


	public void printDocs(){
		Util.pl("""
				**************************************************************************************
				**                        ICD 2 OncoTree Classifier : Nov 2026                      **
				**************************************************************************************
				This tool makes use of the ICD-O (oncology) and ICD-10 (general) to OncoTree mappings 
				published in two Ohlsen et.al. Stud Health Technol Inform publications from 2025 Aug 7
				https://doi.org/10.3233/shti250796 and 2022 May 25, https://doi.org/10.3233/SHTI220464
				to map the available ICD codes from Tempus test reports to the "best" OncoTree tissue
				and tumor node for the OT Comparator. This classifier first attempts to map using the
				ICD-O codes and then ICD-10 codes if the ICD-O mapping fails.

				Options:
				  -o Path to the ICD-O parsed json, see 
				       https://github.com/skfit-uni-luebeck/mapping-to-oncoTree 
				  -1 Path to the ICD-10 parsed json, ditto
				  -t Path to a tab delimited txt file with SampleID IcdOTopo IcdOMorph Icd10 codes,
				       missing values should be coded as 'null'
				  -j Path to a directory to save the calls in json format for the OT Comparator
				  -n Path to a json file representing the OncoTree data structure, e.g. curl -o 
				       otl.json 'https://oncotree.info/api/tumorTypes?version=oncotree_latest_stable
				  
				  
				Example: java -jar OT_0.6.jar ICD -j ClassifiedResults/ -t testIcdCodesParsed.txt
				  -o concept-map-icd-o-oncotreeParsed.json -j otl.json
				  -1 concept-map-icd-10-oncotreeParsed.json 
				  

				**************************************************************************************
				""");
	}



}
