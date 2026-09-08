package edu.hci.oncotree.apps;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import edu.hci.oncotree.misc.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathLabCsvParser {

	//User fields
	private Path csvPath = null;
	private File jsonDir = null;
	private File nameIdKey = null;
	private boolean verbose = false;
	
	//Internal fields
	private Logger log = null;
	
	
	private LinkedHashMap<String, String> idKey = null;
	private Pattern leadingNumber = Pattern.compile("[0-9.\\-]+ (.+)");

	//constructor
	public PathLabCsvParser (String[] args) {
		createLogger();
		
		try {
			processArgs(args);
			idKey = Util.loadFileIntoHash(nameIdKey, 0, 1);
			parseCsv();
			
		} catch (Exception e) {
			log.error("ERROR, failed to process your csv file",e);
			System.exit(1);
		}
	}

	// Ridiculously complex for no reason! This is why avoid using loggers.
	private void createLogger() {
		LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
		ch.qos.logback.classic.Logger classLogger = ctx.getLogger(PathLabCsvParser.class);
		classLogger.setLevel(Level.INFO);
		log = classLogger;
	}

	public void parseCsv() throws Exception {
		log.info("Parsing csv...");
		
		CSVFormat format = CSVFormat.DEFAULT.builder()
				.setHeader()
				.setSkipHeaderRecord(true)
				.get();

		Reader reader = Files.newBufferedReader (csvPath, StandardCharsets.UTF_8);

		CSVParser parser = format.parse(reader);
		for (CSVRecord record : parser) {

			String pa = record.get("PROVIDED_ACCESSION");
			log.debug(pa);

			//fetch OT ID
			String otId = idKey.get(pa);
			if (otId == null) throw new Exception("Failed to find a OT ID for "+pa);

			StringBuilder sb = new StringBuilder();

			appendText(sb, "\n---SP Synoptic---\n", record.get("SP Synoptic"));

			appendText(sb, "\n---SP Micro---\n", record.get("SP Micro"));

			appendText(sb, "\n---SP Diagnosis---\n", record.get("SP Diagnosis"));

			appendText(sb, "\n---SP Comment---\n", record.get("SP Comment"));

			appendText(sb, "\n---SP Immuno---\n", record.get("SP Immuno"));

			appendText(sb, "\n---FNA Interp---\n", record.get("FNA Interp"));

			appendText(sb, "\n---FNA Comment---\n", record.get("FNA Comment"));

			appendText(sb, "\n---FNA Specimen---\n", record.get("FNA Specimen"));

			appendText(sb, "\n---ADDENDA---\n", record.get("ADDENDA"));

			//attempt to parse sample site
			String sampleSite = null;

			//if tumor site is present
			String[] split = sb.toString().split("\\n");
			for (String l: split) {
				String parsed = l.trim().toLowerCase();
				if (parsed.startsWith("tumor site:")) {
					parsed = Util.COLON.split(parsed)[1];
					if (parsed.contains(",")) {
						parsed = Util.COMMA.split(parsed)[0];
					}
					log.debug("\tTumor Site:\t"+parsed);
					sampleSite = parsed.trim();
				}
			}

			//SP Diagnosis? or FNA Interp
			if (sampleSite == null) {
				String diagnosis = record.get("SP Diagnosis");
				if (diagnosis == null || diagnosis.equals("NA")) diagnosis = record.get("FNA Interp");

				if (diagnosis!=null && diagnosis.equals("NA") == false) {
					diagnosis = diagnosis.trim();
					diagnosis = diagnosis.replace("\"", "'");
					diagnosis = diagnosis.replace("\r", "");
					String[] lines = diagnosis.split("\\n");
					for (String l: lines) {
						//Util.pl("\t\tLooking at: '"+l+"'");
						l= l.trim();
						if (l.length()==0) continue;
						l = l.toLowerCase();
						if (l.contains("preliminary")) continue;
						//OK found something, trim it?
						if (l.contains(",")) {
							l = Util.COMMA.split(l)[0];
						}
						//leading number?
						Matcher mat = leadingNumber.matcher(l);
						if (mat.matches()) l= mat.group(1);
						sampleSite = l;
						break;
					}
					log.debug("\tSP Diagnosis:\t"+sampleSite);
				}
			}

			if (sampleSite == null) log.debug("\tNF");

			JSONObject jo = new JSONObject();
			jo.put("test_order_id", otId);
			jo.put("path_lab_info", sb.toString());
			if (sampleSite !=null) jo.put("sample_site", sampleSite);
			else jo.put("sample_site", "");
			jo.put("icd_code_descriptions", "");

			File out = new File(jsonDir, otId+".json");
			Util.writeString(jo.toString(3), out);
			log.info("\t"+out.getName());

		}
		reader.close();

	}

	private void appendText(StringBuilder sb, String label, String txt) {
		txt = txt.trim();
		if (txt.length() == 0 || txt.equals("NA")) return;
		sb.append(label);
		txt = txt.replace("\"", "'");
		txt = txt.replace("\r", "");
		sb.append(txt);
		sb.append("\n");
	}

	/*public static void main(String[] args) throws Exception {

		File csv = new File("/Users/u0028003/HCI/ClinicalGenomics/PathologyReports/reports_only_oncotree_NSpies_11Aug2026.csv");
		File jsonDir = new File("/Users/u0028003/HCI/ClinicalGenomics/PathologyReports/ParsedArupOTJson");
		jsonDir.mkdirs();
		File key = new File("/Users/u0028003/HCI/ClinicalGenomics/PathologyReports/arupAIDsOTIDs.txt");

		PathLabCsvParser acp = new PathLabCsvParser(csv, jsonDir, key);
		acp.parseCsv();
	}*/
	
	public static void main(String[] args) {
		new PathLabCsvParser(args);
	}		

	/**This method will process each argument and assign new variables
	 * @throws FileNotFoundException */
	public void processArgs(String[] args) throws Exception {
		//any args?
		if (args.length ==0) {
			printDocs();
			System.exit(0);
		}

		Pattern pat = Pattern.compile("-[a-z]");
		log.info("OncoTreeClassifier Arguments: {}\n", Util.stringArrayToString(args, " "));
		File csvFile = null;
		
		for (int i = 0; i<args.length; i++){
			String lcArg = args[i].toLowerCase();
			Matcher mat = pat.matcher(lcArg);
			if (mat.matches()){
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 'c': csvFile = new File(args[++i]); break;
					case 'i': nameIdKey= new File(args[++i]); break;
					case 'r': jsonDir = new File(args[++i]).getCanonicalFile(); break;
					case 'v':
					    verbose = true;
					    ((ch.qos.logback.classic.Logger) log).setLevel(Level.DEBUG);
					    break;
					default: 
						log.error("Problem, unknown option!" + mat.group());
						System.exit(1);
					}
				}
				catch (Exception e){
					log.error("Sorry, something doesn't look right with this parameter: -"+test);
					System.exit(1);
				}
			}
		}
		
		boolean errorFound = false;
		if (csvFile == null || csvFile.exists()== false) {
			log.error("ERROR: Cannot find your cvs file to parse? "+csvFile+"\n");
			errorFound = true;
		}
		if (nameIdKey == null || nameIdKey.exists()== false) {
			log.error("ERROR: Cannot find path ID to shadow ID file? "+nameIdKey+"\n");
			errorFound = true;
		}
		if (jsonDir == null) {
			log.error("ERROR: Cannot find your results directory for saving the jsons? "+jsonDir+"\n");
			errorFound = true;
		}
		
		// don't make dirs unless everything looks OK
		if (errorFound==false) {
			jsonDir.mkdirs();
			csvPath = csvFile.toPath();
		}
		
		printParams();
		if (errorFound) {
			log.error("Correct errors and restart.");
			System.exit(1);
		}
	}
	
	public void printParams() {
		log.info("""
				Run Parameters:
				\t-c CSV file to parse  {}
				\t-i ID conversion file {}
				\t-r Results directory  {}
				\t-v Verbose            {}
				""",
				csvPath.getFileName(), nameIdKey.getName(), jsonDir.getName(), verbose);
	}


	public void printDocs(){
		log.info("""
				
				
				**************************************************************************************
				**                          Path Lab Csv Parser : September 2026                    **
				**************************************************************************************
				This tool parses pathology reports in discrete field csv format into the four field 
				json input required by the OncoTreeClassifier.  The csv header should contain:
				PROVIDED_ACCESSION and one or more of 'SP Synoptic', 'SP Micro', 'SP Diagnosis', 
				'SP Comment', 'SP Immuno', 'FNA Interp', 'FNA Comment', 'FNA Specimen', 'ADDENDA'.

				Options:
				  -c Path to the path lab csv file
				  -i Path to a two column tab delimited file containing the 'PPROVIDED_ACCESSION'
				        and shadow IDs to replace them in the json output.
				  -r Path to a directory to write the json results
				  -v Verbose
				  
				Example: java -jar OT_0.1.jar PathLabCsvParser -r ParsedJsonsForClassification/ 
				  -c arupPathReports.csv -i arupIdsShadowIds.txt -v

				**************************************************************************************
				""");
	}

}
