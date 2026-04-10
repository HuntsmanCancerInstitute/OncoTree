package edu.hci.oncotree.parsers;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import edu.hci.oncotree.misc.Util;

/**{
"test_order_id": "0003JHX71O", 
"oncotree_code": "PAAD", 
"oncotree_tissue": "Pancreas", 
"confidence": 5, 
"rationale": "Direct match between 'Adenocarcinoma' and 'Pancreatic Adenocarcinoma'. ICD-O: Adenocarcinoma"
}
*/
public class Call {

	private String testOrderId = null;
	private String oncoTreeCode = null;
	private String oncoTreeTissue = null;
	private int confidence = -1;
	private String rational = null;
	
	public Call(String callSet) {
		JSONObject jo = new JSONObject(callSet);
		testOrderId = jo.getString("test_order_id");
		oncoTreeCode = jo.getString("oncotree_code").toUpperCase();
		confidence = jo.getInt("confidence");
		if (jo.has("oncotree_tissue")) oncoTreeTissue = jo.getString("oncotree_tissue");
		if (jo.has("rationale")) rational = jo.getString("rationale");
		else rational = jo.getString("rational");
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Test Order ID : "); sb.append(testOrderId); sb.append("\n");
		sb.append("OncoTree Code : "); sb.append(oncoTreeCode); sb.append("\n");
		sb.append("OncoTree Tissue : "); sb.append(oncoTreeTissue); sb.append("\n");
		sb.append("Confidence : "); sb.append(confidence); sb.append("\n");
		sb.append("Rational : "); sb.append(rational); 
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

	public int getConfidence() {
		return confidence;
	}

	public String getRational() {
		return rational;
	}
	

}