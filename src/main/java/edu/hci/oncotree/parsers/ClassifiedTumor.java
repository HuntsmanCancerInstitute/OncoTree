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
	private boolean skipNodeClassification = false;
	private boolean tissueClassificationOK = false;
	private boolean nodeClassificationOK = false;

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
		oncoTreeTissueCode = tissueClassification.getString("oncotree_tissue_code").toUpperCase();
	}

	public void setNodeClassification(JSONObject nodeClassification) {
		this.nodeClassification = nodeClassification;
		if (nodeClassification.has("oncotree_code")) oncoTreeNodeCode = nodeClassification.getString("oncotree_code").toUpperCase();
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

	public void saveFinalJson(String model, File finalClassificationDir) {
		File j = new File(finalClassificationDir, testOrderId+"."+oncoTreeTissueCode+"."+oncoTreeNodeCode+".json");
		JSONObject jo = new JSONObject();
		jo.put("test_order_id", testOrderId);
		jo.put("tissue_classification_ok", tissueClassificationOK);
		if (oncoTreeTissueCode!=null) {
			jo.put("oncotree_tissue_code", oncoTreeTissueCode);
			jo.put("tissue_confidence", tissueClassification.get("confidence"));
		}
		jo.put("node_classification_ok", nodeClassificationOK);
		//sometimes this will be NONE when the tissue code is NONE
		jo.put("oncotree_node_code", oncoTreeNodeCode);
		if (nodeClassification!=null) jo.put("node_confidence", nodeClassification.get("confidence"));
		jo.put("model", model);
		jo.put("date_classified", Util.getDate());
		Util.writeString(jo.toString(3), j);
	}

	public void setSkipNodeClassification(boolean b) {
		skipNodeClassification = b;
		
	}

	public boolean isSkipNodeClassification() {
		return skipNodeClassification;
	}

	public void setOncoTreeNodeCode(String oncoTreeNodeCode) {
		this.oncoTreeNodeCode = oncoTreeNodeCode;
	}

	public boolean isTissueClassificationOK() {
		return tissueClassificationOK;
	}

	public void setTissueClassificationOK(boolean tissueClassificationOK) {
		this.tissueClassificationOK = tissueClassificationOK;
	}

	public boolean isNodeClassificationOK() {
		return nodeClassificationOK;
	}

	public void setNodeClassificationOK(boolean nodeClassificationOK) {
		this.nodeClassificationOK = nodeClassificationOK;
	}
}
