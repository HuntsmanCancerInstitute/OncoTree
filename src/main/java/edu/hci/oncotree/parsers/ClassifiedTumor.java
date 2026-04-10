package edu.hci.oncotree.parsers;

import java.io.File;

import org.json.JSONObject;

import edu.hci.oncotree.misc.Util;

public class ClassifiedTumor {

	//fields
	private File tumorInfoJson = null;
	private JSONObject tumorInfo = null;
	private JSONObject tissueClassification = null;
	private String oncoTreeTissueCode = null;
	private JSONObject nodeClassification = null;
	private String oncoTreeNodeCode = null;
	private String testOrderId = null;

	public ClassifiedTumor (File tumorInfoJson) {
		this.tumorInfoJson = tumorInfoJson;
		String jsonString = Util.loadFile(tumorInfoJson, "\n", true);
		tumorInfo = new JSONObject(jsonString);
		testOrderId = tumorInfo.getString("test_order_id");
	}

	public String getTestOrderId() {
		return testOrderId;
	}

	public File getTumorInfoJson() {
		return tumorInfoJson;
	}

	public JSONObject getTumorInfo() {
		return tumorInfo;
	}

	public JSONObject getTissueClassification() {
		return tissueClassification;
	}

	public JSONObject getNodeClassification() {
		return nodeClassification;
	}

	public void setTumorInfoJson(File tumorInfoJson) {
		this.tumorInfoJson = tumorInfoJson;
	}

	public void setTissueClassification(JSONObject tissueClassification){
		this.tissueClassification = tissueClassification;
		if (tissueClassification.has("oncotree_tissue_code")) oncoTreeTissueCode = tissueClassification.getString("oncotree_tissue_code");
		//no real tissue code has a / so these are all wrong
		oncoTreeTissueCode = oncoTreeTissueCode.replace("/", "_");
	}
	
	public void setTissueClassification(File json){
		String jsonString = Util.loadFile(json, "\n", true);
		tissueClassification = new JSONObject(jsonString);
		oncoTreeTissueCode = tissueClassification.getString("oncotree_tissue_code");
	}

	public void setNodeClassification(JSONObject nodeClassification) {
		this.nodeClassification = nodeClassification;
		if (nodeClassification.has("oncotree_code")) oncoTreeNodeCode = nodeClassification.getString("oncotree_code");
	}
	
	public void setNodeClassification(File json){
		String jsonString = Util.loadFile(json, "\n", true);
		nodeClassification = new JSONObject(jsonString);
		oncoTreeNodeCode = nodeClassification.getString("oncotree_code");
	}

	public String getOncoTreeTissueCode() {
		return oncoTreeTissueCode;
	}

	public void saveTissueJson(File tissueJsonDir) {
		File j = new File(tissueJsonDir, testOrderId+"."+oncoTreeTissueCode+".json");
		Util.writeString(tissueClassification.toString(3), j);
	}
	
	public void saveNodeJson(File nodeJsonDir) {
		File j = new File(nodeJsonDir, testOrderId+"."+oncoTreeTissueCode+"."+oncoTreeNodeCode+".json");
		Util.writeString(nodeClassification.toString(3), j);
	}

	public String getOncoTreeNodeCode() {
		return oncoTreeNodeCode;
	}
}
