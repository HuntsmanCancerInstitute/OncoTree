package edu.hci.oncotree.misc;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {

	public static void main(String[] args) {
		String badResult = Util.loadFile(new File("/Users/u0028003/Downloads/badResult.txt"), "\n", false);
		Util.pl(parseJsonResult(badResult));
	}
	
	/**Sometimes the LLM corrects itself and issues a second json result, so just want to take the last and skip the first.*/
	private static String parseJsonResult(String result) {
		String[] lines = result.split("\n");
		
		//find last {
		int lastForwardIndex = -1;
		int lastReverseIndex = -1;
		for (int i=0; i< lines.length; i++) {
			lines[i] = lines[i].trim();
			if (lines[i].startsWith("{")) lastForwardIndex =  i;
			if (lines[i].startsWith("}")) lastReverseIndex =  i;
		}
		//either missing
		if (lastForwardIndex == -1 || lastReverseIndex == -1) return null;
		
		//forward not less than reverse?
		if (lastReverseIndex < lastForwardIndex) return null;
		
		StringBuilder sb = new StringBuilder();
		for (int i=lastForwardIndex; i<=lastReverseIndex; i++) {
			sb.append(lines[i]);
			sb.append("\n");
		}
		
		return sb.toString();

	}

}
