package edu.hci.oncotree.parsers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;
import edu.hci.oncotree.misc.Util;

/**
 * Get the latest, e.g. curl -o oncotreeModelLatest.json "https://oncotree.info/api/tumorTypes?version=oncotree_latest_stable"
 * */
public class CallParser {
	
	//fields
	private File jsonl = null;
	private HashMap<String, Call> testIdCall = null;	
	
	//constructors
	public CallParser (File jsonl) throws Exception {
		this.jsonl = jsonl;
		parseJson();
	}


	private void parseJson() {
		String[] tumorCalls = Util.loadFile(jsonl);
		testIdCall = new HashMap<String, Call>();
		//for each tumorCall
		for (String tc: tumorCalls) {
			Call c = new Call(tc);
			testIdCall.put(c.getTestOrderId(), c);
		}
	}

	public static void main(String[] args) throws Exception {
		CallParser cp = new CallParser (new File ("/Users/u0028003/HCI/Hackathons/2025/OncoTreeMapper/Calls/Claude/claude_test_results_AllData_n399.jsonl"));
	}


	public HashMap<String, Call> getTestIdCall() {
		return testIdCall;
	}


}
