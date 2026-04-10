package edu.hci.oncotree.parsers;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import org.json.JSONObject;

import edu.hci.oncotree.misc.Util;

/**
{
"test_order_id": "01IH7HG27S",
"oncotree_tissue_code": "BOWEL",
"reasoning": "The tumor is a primary adenocarcinoma of the colon as described in the report and icd_code_descriptions, which maps to the Bowel (Colon) tissue category.",
"confidence": "high"
}
*/
public class TissueCall {

	private String testOrderId = null;
	private String oncoTreeCode = null;
	private String oncoTreeTissue = null;
	private String confidence = null;
	private String reasoning = null;
	
	public TissueCall(String jsonString, HashSet<String> tissueCodes, boolean verbose, File json) throws Exception {
		try {
			JSONObject jo = new JSONObject(jsonString);
			//if any don't exist this throws an exception
			testOrderId = jo.getString("test_order_id");
			oncoTreeCode = jo.getString("oncotree_tissue_code").toUpperCase();
			confidence = jo.getString("confidence").toUpperCase();
			reasoning = jo.getString("reasoning");
			//check it
			if (tissueCodes.contains(oncoTreeCode)==false && verbose) {
				Util.el("Failed to find the oncotree_tissue_code in the valid codes for "+jsonString);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Util.printErrAndExit("Failed to properly parse "+json);
		}
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Test Order ID : "); sb.append(testOrderId); sb.append("\n");
		sb.append("OncoTree Code : "); sb.append(oncoTreeCode); sb.append("\n");
		sb.append("Confidence : "); sb.append(confidence); sb.append("\n");
		sb.append("Reasoning : "); sb.append(reasoning); 
		return sb.toString();
	}

	public String getTestOrderId() {
		return testOrderId;
	}

	public String getOncoTreeCode() {
		return oncoTreeCode;
	}

	public String getOncoTreeTissue() {
		return oncoTreeTissue;
	}

	public String getReasoning() {
		return reasoning;
	}

	public String getConfidence() {
		return confidence;
	}
	

}