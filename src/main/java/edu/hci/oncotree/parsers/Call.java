package edu.hci.oncotree.parsers;

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

	//should always be present
	private String testOrderId = null;
	private boolean nodeClassificationOK = false;
	private boolean tissueClassificationOK = false;
	
	//sometimes null
	private String nodeCode = null;
	private String tissueCode = null;
	private String nodeConfidence = null;
	private String tissueConfidence = null;

	public Call(String callSet) {
		try {
			JSONObject jo = new JSONObject(callSet);
			testOrderId = jo.getString("test_order_id");
			tissueClassificationOK = jo.getBoolean("tissue_classification_ok");
			nodeClassificationOK = jo.getBoolean("node_classification_ok");

			if (tissueClassificationOK) {
				tissueCode = jo.getString("oncotree_tissue_code").toUpperCase();
				tissueConfidence = jo.getString("tissue_confidence");
			}
			if (nodeClassificationOK) {
				nodeCode = jo.getString("oncotree_node_code").toUpperCase();
				if (jo.has("node_confidence")) nodeConfidence = jo.getString("node_confidence");
			}
		} catch (Exception e) {
			Util.el("Problem parsing call data from: \n"+callSet);
			e.printStackTrace();
			System.exit(1);
		}
	}

	public String getTestOrderId() {
		return testOrderId;
	}
	public boolean isNodeClassificationOK() {
		return nodeClassificationOK;
	}
	public boolean isTissueClassificationOK() {
		return tissueClassificationOK;
	}
	public String getNodeCode() {
		return nodeCode;
	}
	public String getTissueCode() {
		return tissueCode;
	}
	public String getNodeConfidence() {
		return nodeConfidence;
	}
	public String getTissueConfidence() {
		return tissueConfidence;
	}
}