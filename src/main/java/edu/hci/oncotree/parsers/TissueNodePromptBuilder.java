package edu.hci.oncotree.parsers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import edu.hci.oncotree.misc.Util;

public class TissueNodePromptBuilder {
	
	private HashMap<String,String> tissueCodeNodeCodes = new HashMap<String,String>();
	private HashSet<String> allNodeCodes = new HashSet<String>();
	private HashMap<String,String> tissueCodeCatalog = new HashMap<String,String>();
	private HashMap<String,String> tissueCodeExamples = new HashMap<String,String>();
	private HashMap<String,String> tissueCodeExampleResponses = new HashMap<String,String>();
	
	public TissueNodePromptBuilder(File tissueNodes, File tissueCatalogDir, File tissueExampleDir) throws IOException {
		loadTissueNodeCodes(tissueNodes);
		loadTissueCodeCatalog(tissueCatalogDir);
		loadTissueExamples(tissueExampleDir);
	}
	
	public TissueNodePromptBuilder(File tissueNodes, File tissueCatalogDir) throws IOException {
		loadTissueNodeCodes(tissueNodes);
		loadTissueCodeCatalog(tissueCatalogDir);
	}
	
	public static void main (String[] args) throws IOException {
		
		File tissueNodes = new File("/Users/u0028003/Downloads/OTResources6July2026/tissueCodeNodeCodes.txt");
		File tissueCatalogDir = new File("/Users/u0028003/Downloads/OTResources6July2026/TissueNodeCatalog");
		File tissueExampleDir = new File("/Users/u0028003/Downloads/OTResources6July2026/TissueNodeExamples");
		TissueNodePromptBuilder builder = new TissueNodePromptBuilder(tissueNodes, tissueCatalogDir, tissueExampleDir);
		//Util.pl("\n"+builder.fetchPrompt("EYE"));
		//Util.pl("\n"+builder.fetchPromptGenericExamples("EYE"));
	}
	
	public String fetchPrompt(String tissueCode) {
		//check if present
		if (tissueCodeExamples.containsKey(tissueCode)==false || tissueCodeExampleResponses.containsKey(tissueCode)==false  || tissueCodeNodeCodes.containsKey(tissueCode)==false || tissueCodeCatalog.containsKey(tissueCode)==false) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(prePrompt);
		sb.append(tissueCodeCatalog.get(tissueCode));
		sb.append("════════════════════════\n");
		sb.append(getExample(tissueCode));
		sb.append(getResponse(tissueCode));
		sb.append(getExampleResponse(tissueCode));
		return sb.toString();
	}
	
	public String fetchPromptGenericExamples(String tissueCode) {
		//check if present
		if (tissueCodeNodeCodes.containsKey(tissueCode)==false || tissueCodeCatalog.containsKey(tissueCode)==false) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(prePrompt);
		sb.append(tissueCodeCatalog.get(tissueCode));
		sb.append("════════════════════════\n");
		sb.append(genericTissuePrompt);
		sb.append(getGenericResponse(tissueCode));
		sb.append("\n");
		return sb.toString();
	}

	private void loadTissueCodeCatalog(File tissueCatalogDir) {
		File[] txts = Util.extractFiles(tissueCatalogDir, ".txt");
		for (File f: txts) {
			String tissueCode = f.getName().substring(0, f.getName().length()-4);
			String catalog = Util.loadFile(f, "\n", false);
			tissueCodeCatalog.put(tissueCode, catalog);
		}
	}
	
	private void loadTissueExamples(File dir) throws IOException {
		File[] txts = Util.extractFiles(dir, ".json");
		for (File file: txts) {
			String fileName = file.getName();
			String[] f = fileName.split("\\.");
			if (f.length !=3) throw new IOException("Failed to parse three fields from the example json "+fileName);
			if (fileName.contains("example")) {
				tissueCodeExamples.put(f[0], Util.loadFile(file, "\n", false));
			}
			else if (fileName.contains("response")) {
				tissueCodeExampleResponses.put(f[0], Util.loadFile(file, "\n", false));
			}
		}
	}

	private static String prePrompt =
			"""
			You are an expert pathologist who specializes in tumor classification. 
			Your task is to map the tumor report provided to the best OncoTree Node in the following catalog.
			The nodes are organized along branches. The further out a node is along a branch the more specific the tumor classification.  
			The root node is the first in the catalog and has a "parent_oncotree_code" called "TISSUE".  
			Use the "parent_oncotree_code" and "children_oncotree_codes" in each node to understand the catalog structure.

			ONCOTREE NODE CATALOG:
			════════════════════════
			
			""";
	
	private static String genericTissuePrompt =
			"""
			
			Your task is to find the node from the catalog above that best matches the tumor report information below.
			The more specific the better.  That said, don't overdo it. Better a more conservative classification than something speculative. Sometimes the best match is the root node.
			Weight the tumor "path_lab_info" more than the "icd_code_descriptions" when choosing between close classifications.
			Do not assume any mutant information if it isn't provided.  Pick the preceeding classification node.  For example, a BRAIN Astrocytoma without IDH mutation information should be classified as ADIFG, Adult-Type Diffuse Glioma.
			Likewise, do not assume a tumor grade unless it is given, take the prior, more conservative classification. High-grade does not map to a Grade 2, 3, or 4.
			Lastly, a carcinoma should not be assumed to be an adenocarcinoma. The pathologist would have called it an adenocarcinoma if they could. So pick a "carcinoma" classification.  That said, if the tumor information says "adenocarcinoma" then pick a classification with that designation.

			Follow this RESPONSE FORMAT in JSON:
			{
				"test_order_id": "<the test_order_id provided in the tumor report>",
				"oncotree_code": "<One of the oncotree_codes from the ONCOTREE NODE COLLECTION above.>,"
				"confidence": "<high | medium | low>",
				"reasoning": "<1-2 sentence explanation>"
			}

			Here is an EXAMPLE BLADDER tumor report example:
			{
				"icd_code_descriptions": "Malignant neoplasm of bladder; Malignant neoplasm of bladder, unspecified; Transitional cell carcinoma; Bladder",
				"path_lab_info": "Invasive high grade papillary urothelial carcinoma",
				"test_order_id": "432R89DK6U",
 				"sample_site": "Bladder, deep left lateral wall"
			}
			Here is a EXAMPLE response to the BLADDER tumor example:
			{
				"test_order_id": "432R89DK6U",
				"oncotree_code": "UPA",
				"reasoning": "Urothelial Papilloma but no mention of inverted so not IUP.",
				"confidence": "high"
			}

			Here is an EXAMPLE BRAIN tumor report example:
			{
				"icd_code_descriptions": "Malignant neoplasm of central nervous system, unspecified; Astrocytoma; Brain",
				"path_lab_info": "Astrocytoma, IDH-mutant, WHO grade 2",
				"test_order_id": "CCCJP7S5SF",
				"sample_site": "Brain, right"
			}
			Here is a EXAMPLE response to the BRAIN tumor example:
			{
				"test_order_id": "CCCJP7S5SF",
				"oncotree_code": "ASTR2",
				"reasoning": "Near direct match to Astrocytoma, IDH-Mutant, Grade 2",
				"confidence": "high"
			}

			Here is an EXAMPLE SKIN tumor report example:
			{
				"icd_code_descriptions": "Malignant melanoma of skin; Malignant melanoma of skin, unspecified; Malignant melanoma of skin, unspecified; Malignant melanoma; Skin",
				"path_lab_info": "Metastatic melanoma",
				"test_order_id": "3VARQVRCHTRR",
				"sample_site": "Lymph node, right inguinal"
			}
			Here is a EXAMPLE response to the SKIN tumor example:
			{
				"test_order_id": "3VARQVRCHTRR",
				"oncotree_code": "MEL",
				"reasoning": "No additional information other than 'melanoma' so cannot further subtype.",
				"confidence": "high"
			}
			""";
	
			
	private String getExample(String tissueCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("\nHere is an example tumor report in JSON format:\n");
		sb.append(tissueCodeExamples.get(tissueCode));
		sb.append("""
				Your task is to find the node from the catalog that best matches the tumor report information.  
				The more specific the better.  That said, don't overdo it. Better a more conservative classification than something speculative. Sometimes the best match is the root node.
				Weight the tumor "path_lab_info" more than the "icd_code_descriptions" when choosing between close classifications.
				Do not assume any mutant information if it isn't provided.  Pick the preceeding classification node.  For example, a BRAIN Astrocytoma without IDH mutation information should be classified as ADIFG, Adult-Type Diffuse Glioma.
				Likewise, do not assume a tumor grade unless it is given, take the prior, more conservative classification. High-grade does not map to a Grade 2, 3, or 4.
				Lastly, a carcinoma should not be assumed to be an adenocarcinoma. The pathologist would have called it an adenocarcinoma if they could. So pick a "carcinoma" classification.  That said, if the tumor information says "adenocarcinoma" then pick a classification with that designation.
				""");
		return sb.toString();
	}
	
	private String getResponse(String tissueCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("""
				\nRESPONSE FORMAT in JSON:
				{
				   "test_order_id": "<the test_order_id provided in the tumor report>",
				   "oncotree_code": "<One of the oncotree_codes from the ONCOTREE NODE COLLECTION above.>,"
				   "confidence": "<high | medium | low>",
				   "reasoning": "<1-2 sentence explanation>"
				}
				
				Be certain your response contains 4 items: test_order_id, oncotree_code, confidence, and reasoning.
				""");
		sb.append("Be certain your response \"oncotree_code\" is one of the items in this list: ");
		sb.append(tissueCodeNodeCodes.get(tissueCode));
		sb.append("\nIf either requirement is not met, re run the classification.\n");
		return sb.toString();
	}
	
	private String getGenericResponse(String tissueCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("""
				
				Be certain your response contains 4 items: test_order_id, oncotree_code, confidence, and reasoning.
				""");
		sb.append("Be certain your response \"oncotree_code\" is one of the items in this list: ");
		sb.append(tissueCodeNodeCodes.get(tissueCode));
		sb.append("\nIf either requirement is not met, re run the classification.\n");
		return sb.toString();
	}
	
	private String getExampleResponse(String tissueCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("\nHere is a proper response to the tumor example above:\n");
		sb.append(tissueCodeExampleResponses.get(tissueCode));
		return sb.toString();
	}
	
	public static Pattern spaceColon = Pattern.compile(" : ");
	public static Pattern comma = Pattern.compile(",");
	public void loadTissueNodeCodes(File tissueNodeCodes) throws IOException{
		String[] lines = Util.loadFile(tissueNodeCodes);
		// ADRENAL_GLAND : ADRENAL_GLAND, ACA, ACC, PHC, 
		for (String s: lines) {
			s = s.trim();
			if (s.length()>0) {
				String[] f = spaceColon.split(s);
				if (f.length !=2) throw new IOException("Failed to parse two fields from "+s+" in "+tissueNodeCodes);
				String nodeCodes = f[1].substring(0, f[1].length()-1);
				tissueCodeNodeCodes.put(f[0], nodeCodes);
				String[] nc = comma.split(nodeCodes);
				for (String n: nc) allNodeCodes.add(n.trim());
			}
		}
	}

	public HashMap<String, String> getTissueCodeNodeCodes() {
		return tissueCodeNodeCodes;
	}

	public HashSet<String> getAllNodeCodes() {
		return allNodeCodes;
	}
}


