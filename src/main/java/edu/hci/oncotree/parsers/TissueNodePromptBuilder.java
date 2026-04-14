package edu.hci.oncotree.parsers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;

import edu.hci.oncotree.misc.Util;

public class TissueNodePromptBuilder {
	
	private HashMap<String,String> tissueCodeNodeCodes = new HashMap<String,String>();
	private HashMap<String,String> tissueCodeCatalog = new HashMap<String,String>();
	private HashMap<String,String> tissueCodeExamples = new HashMap<String,String>();
	private HashMap<String,String> tissueCodeExampleResponses = new HashMap<String,String>();
	
	public TissueNodePromptBuilder(File tissueNodes, File tissueCatalogDir, File tissueExampleDir) throws IOException {
		loadTissueNodeCodes(tissueNodes);
		loadTissueCodeCatalog(tissueCatalogDir);
		loadTissueExamples(tissueExampleDir);
	}
	
	public static void main (String[] args) throws IOException {
		File tissueNodes = new File("/Users/u0028003/HCI/Hackathons/2025/OncoTreeMapper/OTPrinter9April2026Nodes/TissueNodeExamples/tissueCodeNodeCodes.txt");
		File tissueCatalogDir = new File("/Users/u0028003/HCI/Hackathons/2025/OncoTreeMapper/OTPrinter9April2026Nodes/TissueNodeCatalog");
		File tissueExampleDir = new File("/Users/u0028003/HCI/Hackathons/2025/OncoTreeMapper/OTPrinter9April2026Nodes/TissueNodeExamples");
		TissueNodePromptBuilder builder = new TissueNodePromptBuilder(tissueNodes, tissueCatalogDir, tissueExampleDir);
		Util.pl("\n"+builder.fetchPrompt("EYE"));
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
	
	private String getExample(String tissueCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("\nHere is an example tumor report in JSON format:\n");
		sb.append(tissueCodeExamples.get(tissueCode));
		sb.append("""
				Your task is to find the node from the catalog that best matches the tumor report information.  
				The more specific the better.  That said, don't overdo it. Better a more conservative classification than something speculative. Sometimes the best match is the root node.
				Weight the tumor "original_path_lab_diagnosis" more than the "icd_code_descriptions" when choosing between close classifications.
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
		sb.append("\nIf either requirements are not met, re run the classification.\n");
		return sb.toString();
	}
	
	private String getExampleResponse(String tissueCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("\nHere is a proper response to the tumor example above:\n");
		sb.append(tissueCodeExampleResponses.get(tissueCode));
		return sb.toString();
	}
	
	public static Pattern spaceColon = Pattern.compile(" : ");
	public void loadTissueNodeCodes(File tissueNodeCodes) throws IOException{
		String[] lines = Util.loadFile(tissueNodeCodes);
		// ADRENAL_GLAND : ADRENAL_GLAND, ACA, ACC, PHC, 
		for (String s: lines) {
			s = s.trim();
			if (s.length()>0) {
				String[] f = spaceColon.split(s);
				if (f.length !=2) throw new IOException("Failed to parse two fields from "+s+" in "+tissueNodeCodes);
				tissueCodeNodeCodes.put(f[0], f[1].substring(0, f[1].length()-1));
			}
		}
		
	}

	public HashMap<String, String> getTissueCodeNodeCodes() {
		return tissueCodeNodeCodes;
	}
}


