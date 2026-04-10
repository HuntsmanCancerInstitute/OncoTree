package edu.hci.oncotree.parsers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.json.JSONObject;

import edu.hci.oncotree.misc.Util;

/**Class to parse NCI Thesaurus entries.
 * Get the latest json for each code from https://api-evsrest.nci.nih.gov/api/v1/concept/ncit/C5545?include=full
 * 
 * e.g. 
 * for x in C115460 C121932 C12311 ...
 * do
 * echo $x
 * curl -o $x.j "https://api-evsrest.nci.nih.gov/api/v1/concept/ncit/"$x"?include=full"
 * cat $x.j | jq > $x.json; rm $x.j
 * done
 */
public class NCIThesaurusParser {
	
	//fields
	private File jsonDir = null;
	private HashMap<String, NCItEntry> codeEntry = new HashMap<String, NCItEntry>();
	private boolean verbose = true;
	
	public NCIThesaurusParser (boolean verbose, File jsonDir) throws IOException {
		this.verbose = verbose;
		this.jsonDir = jsonDir;
		parseJsonDirectory();
	}

	private void parseJsonDirectory() throws IOException {
		File[] jsonFiles = Util.extractFiles(jsonDir, "json");
		if (jsonFiles == null) throw new IOException("ERROR extracting json files from "+jsonDir);
		for (File j: jsonFiles) {
			parseJsonFile(j);
		}
		if (verbose) Util.pl("Parsed "+codeEntry.size()+" NCIt entries");
	}

	private void parseJsonFile(File j) {
		if (verbose) Util.pl("Parsing "+j);
		String jString = Util.loadFile(j, " ", true);
		JSONObject jo = new JSONObject(jString);
		NCItEntry n = new NCItEntry(jo);
		codeEntry.put(n.getCode(), n);
		
	}

	private void printEntries() {
		for (NCItEntry e: codeEntry.values()) Util.pl(e+"\n");
	}
	
	/*For testing*/
	public static void main (String[] args) throws IOException {
		NCIThesaurusParser p = new NCIThesaurusParser(true, new File("/Users/u0028003/HCI/Hackathons/2025/OncoTreeMapper/NCItJsons/"));
		p.printEntries();
	}

	public HashMap<String, NCItEntry> getCodeEntry() {
		return codeEntry;
	}
}
