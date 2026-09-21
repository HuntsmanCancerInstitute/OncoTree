package edu.hci.oncotree.apps;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import edu.hci.oncotree.misc.Util;
import edu.hci.oncotree.parsers.ClassifiedTumor;
import edu.hci.oncotree.parsers.TissueNodePromptBuilder;
import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

public class OncoTreeClassifier {

	//user fields
	private File tissuePrompt = null;
	private File[] tumorJsons = null;
	private File resultsDirectory = null;
	private String model = "gemma4:26b"; 
	private String host = "http://localhost:11434";
	private int content = 35000;
	private boolean verbose = false;
	private int timeOutInSeconds = 1200; // 20 min
	private File tissueCodeNodeCodes = null;
	private File tissueNodeCatalogDir = null;
	private String apiKey = null;
	private File apiKeyFile = null;
	private Float temperature = null;
	private int numberAttempts = 5;

	//internal
	private Logger log = null;
	private String tissuePrePrompt = null;
	private TreeMap<String, ClassifiedTumor> testIdClasTum = new TreeMap<String, ClassifiedTumor>();
	private Ollama ollama = null;
	private Options ollamaOptions = null;
	private HashMap<String, File> processedTissueTestIds = new HashMap<String, File>();
	private HashMap<String, File> processedNodeTestIds = new HashMap<String, File>();
	private File tissueJsonDir = null;
	private File nodeJsonDir = null;
	private File finalClassificationDir = null;
	private TissueNodePromptBuilder tissueNodePromptBuilder = null;
	private int numNoneTissueClassifications = 0;
	private int numFailedTissueClassifications = 0;
	private int numFailedNodeClassifications = 0;
	private HashSet<String> allNodeCodes = null;
	private HashMap<String, String> tissueCodes = null;

	public OncoTreeClassifier (String[] args) {
		log = LoggerFactory.getLogger(OncoTreeClassifier.class);

		try {
			long startTime = System.currentTimeMillis();
			processArgs(args);

			//load the prompt
			tissuePrePrompt = Util.loadFile(tissuePrompt, "\n", false);

			//load the tumor jsons
			loadTumorJsons();
			
			//make tissue node builder
			tissueNodePromptBuilder = new TissueNodePromptBuilder(tissueCodeNodeCodes, tissueNodeCatalogDir);
			allNodeCodes = tissueNodePromptBuilder.getAllNodeCodes();
			tissueCodes = tissueNodePromptBuilder.getTissueCodeNodeCodes();
					
			//load any prior results
			loadPriorTissueClassificationResults();
			loadPriorNodeClassificationResults();

			//connect to the ollama server
			connectToOllamaServer();

			//classify the tumor jsons for tissue
			classifyTumorTissuesWithRepeats();

			//classify the tumor jsons for best tissue node
			classifyTumorNodesWithRepeats();
			
			//write out final results
			writeOutFinalResults();

			//any classification issues?
			int exitCode = printStatistics();
			
			//finish and calc run time
			double diffTime = ((double)(System.currentTimeMillis() -startTime))/60000;
			log.info("Done! "+Math.round(diffTime)+" Min\n");
			
			System.exit(exitCode);
			

		} catch (Exception e) {
			log.error("\nERROR running the OncoTreeLLMClassifier", e);
			System.exit(1);
		}

	}
	
	private int printStatistics() {
		log.info("""
				Tissue and Node Classification Statistics:
				# Tumor Samples {}
				# NONE Reported {}
				# Failed Tissue {}
				# Failed Node   {}
				""", testIdClasTum.size(), numNoneTissueClassifications, numFailedTissueClassifications, numFailedNodeClassifications);
		int numErrors = numFailedTissueClassifications + numFailedNodeClassifications;
		if (numErrors !=0) {
			log.error("ERROR: classification completed but "+numErrors+" errors were observed, see above and resolve.");
			return 1;
		}
		return 0;
	}

	private void writeOutFinalResults() {
		log.info("\nSaving final results...");
		for (ClassifiedTumor ct: testIdClasTum.values()) {
			log.debug("\t"+ct.getSampleId());
			ct.saveFinalJson(model, finalClassificationDir);
		}
		log.info("");
	}

	private Pattern period = Pattern.compile("\\.");
	private void loadPriorTissueClassificationResults() {
		File[] tissueJsons = Util.extractFiles(tissueJsonDir, ".json");
		if (tissueJsons != null) {
			for (File f: tissueJsons) {
				Matcher mat = period.matcher(f.getName());
				if (mat.find()) processedTissueTestIds.put(f.getName().substring(0,mat.start()), f);
				else log.warn("FAILED to parse testId from "+f.getName());
			}
		}
	}
	
	private void loadPriorNodeClassificationResults() {
		File[] nodeJsons = Util.extractFiles(nodeJsonDir, ".json");
		if (nodeJsons != null) {
			for (File f: nodeJsons) {
				Matcher mat = period.matcher(f.getName());
				if (mat.find()) processedNodeTestIds.put(f.getName().substring(0,mat.start()), f);
				else log.warn("FAILED to parse testId from "+f.getName());
			}
		}
	}
	
	/*private void classifyTumorTissuesDepreciated() throws Exception {
		log.info("\nClassifying tumor tissues...");
		
		for (ClassifiedTumor ct: testIdClasTum.values()) {
			log.debug("\t"+ct.getSampleId());
			
			//already processed?
			if (processedTissueTestIds.containsKey(ct.getSampleId())) {
				ct.setTissueClassification(processedTissueTestIds.get(ct.getSampleId()));
				log.info("\t"+ct.getSampleId()+"\t"+ct.getOncoTreeTissueCode()+"\ttissue classified, skipping");
				checkTissueClassification(ct);
				continue;
			}
			
			String result = callOllama(ct, tissuePrePrompt);
			log.debug("Response\n"+result);
			
			if (result == null) {
				numFailedTissueClassifications++;
				ct.setSkipNodeClassification(true);
				ct.setTissueClassificationOK(false);
				ct.setNodeClassificationOK(false);
				ct.setOncoTreeNodeCode("NONE");
			}
			
			else {
				//look for issues and trim the result to just {xxxxx}
				String parsed = parseJsonResult(result);
				log.debug("Parsed\n"+parsed);
				if (parsed == null) throw new Exception("Failed to parse a json response object from \n"+ result);

				JSONObject jo = new JSONObject(parsed);
				ct.setTissueClassification(jo);
				log.info("\t"+ct.getSampleId()+ "\t"+ct.getOncoTreeTissueCode());
				checkTissueClassification(ct);

				//write out parsed result
				ct.saveTissueJson(tissueJsonDir);
			}
		}
	}*/
	
	/*private void checkTissueClassification(ClassifiedTumor ct) {
		String tc = ct.getOncoTreeTissueCode();
		//is it NONE? This is OK
		if (tc.equals("NONE")) {
			numNoneTissueClassifications++;
			ct.setSkipNodeClassification(true);
			ct.setOncoTreeNodeCode("NONE");
			ct.setTissueClassificationOK(true);
			ct.setNodeClassificationOK(true);
			log.warn("WARNING: NONE tissue code, manually classify "+ct.getSampleId());
		}
		//is it a legitimate OT tissue code?
		else if (tissueCodes.containsKey(tc)==false) {
			numFailedTissueClassifications++;
			ct.setSkipNodeClassification(true);
			ct.setTissueClassificationOK(false);
			ct.setNodeClassificationOK(false);
			log.error("ERROR: tissue code "+tc + " is not a valid OT Tissue Code, check the tissue classification for "+ct.getSampleId());
		}
		else ct.setTissueClassificationOK(true);
	}*/

	private void failTissueClassification(ClassifiedTumor ct) {
		numFailedTissueClassifications++;
		ct.setSkipNodeClassification(true);
		ct.setTissueClassificationOK(false);
		ct.setNodeClassificationOK(false);
		ct.setOncoTreeNodeCode("NONE");
		log.error("ERROR: Failed to classify the tissue code for "+ct.getSampleId()+". Manually classify it.");
	}
	
	private void classifyTumorTissuesWithRepeats() throws Exception {
		log.info("\nClassifying tumor tissues...");

		for (ClassifiedTumor ct: testIdClasTum.values()) {

			//attempt to classify with retries
			for (int i=0; i< numberAttempts; i++) {
				boolean lastAttempt = ((i+1) == numberAttempts);

				if (i>0)log.info("\t"+ct.getSampleId()+"\t"+(i+1)+"\tAttempt");

				//already processed?
				if (processedTissueTestIds.containsKey(ct.getSampleId())) {
					ct.setTissueClassification(processedTissueTestIds.get(ct.getSampleId()));
					log.info("\t"+ct.getSampleId()+"\t"+ct.getOncoTreeTissueCode()+"\ttissue classified, skipping");
					checkTissueClassificationWithRepeats(ct, true); //this also sets some objects
					break;
				}

				String result = callOllama(ct, tissuePrePrompt);
				log.debug("Response\n"+result);
				
				//only set if at last
				if (result == null && lastAttempt) {
					failTissueClassification(ct);
					break;
				}

				if (result != null) {
					//look for issues and trim the result to just {xxxxx}
					String parsed = parseJsonResult(result);
					log.debug("Parsed\n"+parsed);
					if (parsed == null && lastAttempt) {
						failTissueClassification(ct);
					}
					
					if (parsed != null) {
						JSONObject jo = new JSONObject(parsed);
						ct.setTissueClassification(jo);
						boolean ok = checkTissueClassificationWithRepeats(ct, lastAttempt);
						if (ok) {
							log.info("\t"+ct.getSampleId()+ "\t"+ct.getOncoTreeTissueCode());
							//write out parsed result
							ct.saveTissueJson(tissueJsonDir);
							break;
						}
						//not ok, last attempt?
						if (lastAttempt) {
							failTissueClassification(ct);
							break; //not needed?
						}
					}
				}
				
				//don't do anything, let it run again
			}
		}
	}
	
	private boolean checkTissueClassificationWithRepeats(ClassifiedTumor ct, boolean lastAttempt) {
		String tc = ct.getOncoTreeTissueCode();
		//is it NONE? This is OK
		if (tc !=null && tc.equals("NONE")) {
			numNoneTissueClassifications++;
			ct.setSkipNodeClassification(true);
			ct.setOncoTreeNodeCode("NONE");
			ct.setTissueClassificationOK(true);
			ct.setNodeClassificationOK(true);
			log.warn("WARNING: NONE tissue code, manually classify "+ct.getSampleId());
			return true;
		}
		//is it null or a illegitimate OT tissue code?
		if (tc == null || tissueCodes.containsKey(tc)==false) {
			log.error("ERROR: tissue code "+tc + " is not a valid OT Tissue Code, check the tissue classification for "+ct.getSampleId());
			return false;
		}
		ct.setTissueClassificationOK(true);
		return true;
	}
	
	private void classifyTumorNodesWithRepeats() throws Exception {
		log.info("\nClassifying tumor nodes...");

		for (ClassifiedTumor ct: testIdClasTum.values()) {
			log.debug("\t"+ct.getSampleId());

			//attempt to classify with retries
			for (int i=0; i< numberAttempts; i++) {
				
				boolean lastAttempt = ((i+1) == numberAttempts);
				if (i>0)log.info("\t"+ct.getSampleId()+"\t"+(i+1)+"\tAttempt");

				//check tissue code
				String tissueCode = ct.getOncoTreeTissueCode();
				if (ct.isSkipNodeClassification()) {
					log.info("Skipping node classification for "+ct.getSampleId()+", see messages above.");
					break;
				}

				//already processed?
				if (processedNodeTestIds.containsKey(ct.getSampleId())) {
					ct.setNodeClassification(processedNodeTestIds.get(ct.getSampleId()));
					log.info("\t"+ct.getSampleId()+"\t"+ct.getOncoTreeNodeCode()+"\tnode classified, skipping");
					checkNodeCodeWithRepeats(ct, true);
					break;
				}
				
				String nodePrompt = tissueNodePromptBuilder.fetchPromptGenericExamples(tissueCode);

				String result = callOllama(ct, nodePrompt);
				log.debug("Node Response\n"+result);

				//only set if last attempt
				if (result == null && lastAttempt) {
					failNodeClassification(ct);
					break;
				}

				if (result != null) {
					//look for issues and trim the result to just {xxxxx}
					String parsed = parseJsonResult(result);
					log.debug("Node Parsed\n"+parsed);
					
					//only kill it if last attempt
					if (parsed == null && lastAttempt) {
						failNodeClassification(ct);
						break;
					}
					
					if (parsed != null) {
						JSONObject jo = new JSONObject(parsed);
						ct.setNodeClassification(jo);
						boolean ok = checkNodeCodeWithRepeats(ct, lastAttempt);

						//write out parsed result
						if (ok) {
							log.info("\t"+ct.getSampleId()+ "\t"+ct.getOncoTreeNodeCode());
							ct.saveNodeJson(nodeJsonDir);
							break;
						}
						
						//not ok, last attempt?
						if (lastAttempt) failNodeClassification(ct);
					}
				}
			}
		}
	}
	
	private void failNodeClassification(ClassifiedTumor ct) {
		numFailedNodeClassifications++;
		ct.setOncoTreeNodeCode("NONE");
		ct.setNodeClassificationOK(false);
		log.error("ERROR: Failed to classify the tumor node code for "+ct.getSampleId()+". Manually classify it.");
	}

	/*
	private void classifyTumorNodes() throws Exception {
		log.info("\nClassifying tumor nodes...");
		
		for (ClassifiedTumor ct: testIdClasTum.values()) {
			log.debug("\t"+ct.getSampleId());
			
			//check tissue code
			String tissueCode = ct.getOncoTreeTissueCode();
			if (ct.isSkipNodeClassification()) {
				log.info("Skipping node classification for "+ct.getSampleId()+", see messages above.");
				continue;
			}
			
			//already processed?
			if (processedNodeTestIds.containsKey(ct.getSampleId())) {
				ct.setNodeClassification(processedNodeTestIds.get(ct.getSampleId()));
				log.info("\t"+ct.getSampleId()+"\t"+ct.getOncoTreeNodeCode()+"\tnode classified, skipping");
				checkNodeCode(ct);
				continue;
			}
			String nodePrompt = tissueNodePromptBuilder.fetchPromptGenericExamples(tissueCode);
			
			String result = callOllama(ct, nodePrompt);
			log.debug("Node Response\n"+result);
			
			if (result == null) ct.setNodeClassificationOK(false);
			
			else {
				//look for issues and trim the result to just {xxxxx}
				String parsed = parseJsonResult(result);
				log.debug("Node Parsed\n"+parsed);
				if (parsed == null) throw new Exception("Failed to parse a json response object from \n"+ result);

				JSONObject jo = new JSONObject(parsed);
				ct.setNodeClassification(jo);
				log.info("\t"+ct.getSampleId()+ "\t"+ct.getOncoTreeNodeCode());
				checkNodeCode(ct);

				//write out parsed result
				ct.saveNodeJson(nodeJsonDir);
			}
		}
	}

	private void checkNodeCode(ClassifiedTumor ct) {
		//look for NONE, these must have a tissue classification so set that instead
		if (ct.getOncoTreeNodeCode().equals("NONE")) {
			numNoneTissueClassifications++;
			ct.setNodeClassificationOK(true);
			ct.setOncoTreeNodeCode(ct.getOncoTreeTissueCode());
			log.warn("WARNING: NONE tumor node code, consider manually classifing "+ct.getSampleId()+". Setting it to the tissue code "+ct.getOncoTreeTissueCode());
		}
		//check if it is legitimate
		else if (allNodeCodes.contains(ct.getOncoTreeNodeCode())==false) {
			numFailedNodeClassifications++;
			log.error("ERROR: Node Code "+ct.getOncoTreeNodeCode()+" is not found in OncoTree, see "+ct.getSampleId());
			ct.setNodeClassificationOK(false);
		}
		else ct.setNodeClassificationOK(true);
	}*/
	
	private boolean checkNodeCodeWithRepeats(ClassifiedTumor ct, boolean lastAttempt) {
		String nc = ct.getOncoTreeNodeCode();
		//look for NONE, these must have a tissue classification so set that instead, this is ok
		if (nc.equals("NONE")) {
			numNoneTissueClassifications++;
			ct.setNodeClassificationOK(true);
			ct.setOncoTreeNodeCode(ct.getOncoTreeTissueCode());
			log.warn("WARNING: NONE tumor node code, consider manually classifing "+ct.getSampleId()+". Setting it to the tissue code "+ct.getOncoTreeTissueCode());
			return true;
		}
		//check if it is legitimate
		if (allNodeCodes.contains(ct.getOncoTreeNodeCode())==false) {
			log.error("ERROR: tumor node code "+nc + " is not a valid OT Code, check the node classification for "+ct.getSampleId());
			return false;
		}
		//looks good
		ct.setNodeClassificationOK(true);
		return true;
	}
	
	/**Sometimes the LLM corrects itself and issues a second json result, so just want to take the last and skip the first.*/
	private String parseJsonResult(String result) {
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
		
		
		//check that all four elements are present and no duplicate keys
		HashSet<String> keys = new HashSet<String>();
		boolean ok = true;
		StringBuilder sb = new StringBuilder();
		for (int i=lastForwardIndex; i<=lastReverseIndex; i++) {
			String[] key = Util.COLON.split(lines[i]);
			//duplicate?
			if (keys.contains(key[0])) ok = false;
			keys.add(key[0]);
			sb.append(lines[i]);
			sb.append("\n");
		}
		//don't want to throw a exception just yet since the repeater will pick this up
		if (ok == false) {
			log.error("ERROR: malformed LLM json response, duplicate key found in :\n"+sb);
			return null;
		}
		
		//check that all 4 elements are present
		String sample_id = null;
		String confidence = null;
		String reasoning = null;
		String code = null;
		for (int i=lastForwardIndex; i<=lastReverseIndex; i++) {
			//assign element
			if (lines[i].contains("\"sample_id\"")) sample_id = lines[i];
			else if (lines[i].contains("\"confidence\"")) confidence = lines[i];
			else if (lines[i].contains("\"reasoning\"")) reasoning = lines[i];
			else if (lines[i].contains("\"oncotree_tissue_code\"")) code = lines[i];
			else if (lines[i].contains("\"oncotree_code\"")) code = lines[i];
		}
		if (sample_id==null || confidence==null || reasoning==null || code==null) {
			log.error("ERROR: malformed LLM json response, missing one of the required 4 fields :\n"+sb);
			return null;
		}
		
		//all good
		return sb.toString();

	}


	private String callOllama(ClassifiedTumor tumor, String prompt){

		String classificationRequest = "PLEASE CLASSIFY THIS TUMOR:\n"+ tumor.getTumorInfo().toString(3);

		log.debug("Node prompt submitted to ollama:");
		log.debug(prompt+classificationRequest);
		
		// SYSTEM is the background info, USER is the specific request
		OllamaChatRequest request = OllamaChatRequest.builder()
				.withModel(model)
				.withOptions(ollamaOptions)
				.withMessage(OllamaChatMessageRole.SYSTEM, prompt)
				.withMessage(OllamaChatMessageRole.USER, classificationRequest)
				.build();
		
		boolean ok = false;
		OllamaChatResult result = null;
		for (int i=0; i< numberAttempts; i++) {
			try {
				//both of these will throw exceptions
				result = ollama.chat(request, null);
				checkPromptFit(result);
				
				//must be ok so exit and return result
				ok = true;
				break;
			} catch (Exception e) {
				log.warn("\t"+ tumor.getSampleId()+" '"+e.getLocalizedMessage()+"', relaunching ollama "+i);
			}
		}
		if (ok == false) {
			log.warn("WARNING: classification failed, manually classify "+ tumor.getSampleId());
			return null;
		}
		
		return result.getResponseModel().getMessage().getResponse();
	}

	public void checkPromptFit(OllamaChatResult result) throws Exception {
		if (result == null || result.getResponseModel() == null) throw new Exception("Error with the response or response model. Repeat with -v debugging output enabled.");
		int promptTokens = result.getResponseModel().getPromptEvalCount();

		double usage = (double) promptTokens / content;
		log.debug("\tPrompt token usage: "+promptTokens+", "+ Util.formatNumber(usage, 2));

		if (usage == 1.0) throw new Exception("Prompt was truncated, set higher content than -> " + content);
		if (usage > 0.95) throw new Exception("Prompt ("+promptTokens+") exceeds safe context limit for model, set higher content than -> " + content);
		if (usage > 0.75) {
			log.warn("\nWARNING: Prompt ("+promptTokens+") is using over 75% of context window — classification likely degraded, increase content ("+content+")\n");
		}
	}

	private void connectToOllamaServer() throws Exception {
		log.info("\nConnecting to Ollama server...");

		ollama = new Ollama(host);
		ollama.setRequestTimeoutSeconds(timeOutInSeconds);
		if (apiKey !=null) ollama.setBearerAuth(apiKey);

		// Verify the server is reachable at startup
		if (!ollama.ping()) throw new Exception("Cannot reach Ollama server at " + host + ". Make sure 'ollama serve' is running and the host URL is correctly set.");
		log.debug("Connected to Ollama at " + host);
		
		// Options to make LLM more deterministic, set content
		// Problem here, setting temp 0 let to one tumor never returning from call. So just leaving at default.
		if (temperature == null) {
			ollamaOptions = new OptionsBuilder()
				.setNumCtx(content)
			    .build();
		}
		else {
			ollamaOptions = new OptionsBuilder()
					.setNumCtx(content)
					.setTemperature(temperature)
				    .build();
		}
			    //.setTemperature(0.0f)
			    //.setSeed(42)
			    
		Util.pl("\tModel "+ollamaOptions);
	}

	private void loadTumorJsons() {
		log.info("Loading tumor json files...");
		for (int i=0; i< tumorJsons.length; i++) {
			log.debug("\t"+ tumorJsons[i]);
			ClassifiedTumor ct = new ClassifiedTumor(tumorJsons[i]);
			testIdClasTum.put(ct.getSampleId(), ct);
		}
	}

	public static void main(String[] args) {
		new OncoTreeClassifier(args);
	}		

	/**This method will process each argument and assign new variables
	 * @throws FileNotFoundException */
	public void processArgs(String[] args) throws FileNotFoundException{
		//any args?
		if (args.length ==0) {
			printDocs();
			System.exit(0);
		}

		Pattern pat = Pattern.compile("-[a-z]");
		log.info("OncoTreeClassifier Arguments: {}\n", Util.stringArrayToString(args, " "));
		File tumorJsonDir = null;
		for (int i = 0; i<args.length; i++){
			String lcArg = args[i].toLowerCase();
			Matcher mat = pat.matcher(lcArg);
			if (mat.matches()){
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 't': tissuePrompt = new File(args[++i]); break;
					case 'j': tumorJsonDir = new File(args[++i]); break;
					case 'r': resultsDirectory = new File(args[++i]).getCanonicalFile(); break;
					case 'c': content = Integer.parseInt(args[++i]); break;
					case 'p': numberAttempts = Integer.parseInt(args[++i]); break;
					case 'm': model = args[++i]; break;
					case 'h': host = args[++i]; break;
					case 'e': temperature = Float.parseFloat(args[++i]); break;
					case 's': timeOutInSeconds = Integer.parseInt(args[++i]); break;
					case 'n': tissueCodeNodeCodes = new File(args[++i]); break;
					case 'a': tissueNodeCatalogDir = new File(args[++i]); break;
					case 'k': apiKeyFile = new File(args[++i]); break;
					case 'v':
					    verbose = true;
					    LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
					    ch.qos.logback.classic.Logger classLogger = ctx.getLogger(OncoTreeClassifier.class);
					    classLogger.setLevel(Level.DEBUG);
					    classLogger.setAdditive(false);
					    classLogger.addAppender(ctx.getLogger("ROOT").getAppender("STANDARD"));
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
		if (tissuePrompt == null || tissuePrompt.exists()== false) {
			log.error("ERROR: Cannot find your tissue LLM prompt file, "+tissuePrompt+"\n");
			errorFound = true;
		}
		if (tumorJsonDir == null || tumorJsonDir.exists()== false) {
			log.error("ERROR: Cannot find json tumor directory "+tumorJsonDir+"\n");
			errorFound = true;
		}
		if (tissueCodeNodeCodes == null || tissueCodeNodeCodes.exists()== false) {
			log.error("ERROR: Cannot find your tissue code node codes tissueCodeNodeCodes.txt file, "+tissueCodeNodeCodes+"\n");
			errorFound = true;
		}
		if (tissueNodeCatalogDir == null || tissueNodeCatalogDir.exists()== false) {
			log.error("ERROR: Cannot find your tissue node catalog directory, "+tissueNodeCatalogDir+"\n");
			errorFound = true;
		}
		tumorJsons = Util.extractFiles(tumorJsonDir, ".json");
		if (tumorJsons == null || tumorJsons.length ==0) {
			log.error("ERROR: Failed to find any xxx.json tumor files in "+tumorJsonDir+"\n");
			errorFound = true;
		}
		if (apiKeyFile!=null) {
			host = "https://ollama.com";
			if (apiKeyFile.exists()==false) {
				log.error("ERROR: Failed to find your Ollama key file see the -k option \n");
				errorFound = true;
			}
			String[] p = Util.loadFileAndClean(apiKeyFile);
			if (p.length!=1) {
				log.error("ERROR: Failed to find one line with your key in your Ollama key file see the -k option \n");
				errorFound = true;
			}
			else apiKey = p[0];
		}
		
		if (resultsDirectory == null) {
			log.error("ERROR: Please provide a path to a directory for saving the results\n");
			errorFound = true;
		}
		// don't make dirs unless everything looks OK
		if (errorFound==false) {
			resultsDirectory.mkdirs();
			tissueJsonDir = new File (resultsDirectory, "TissueClassified");
			tissueJsonDir.mkdir();
			nodeJsonDir = new File (resultsDirectory, "NodeClassified");
			nodeJsonDir.mkdir();
			finalClassificationDir = new File (resultsDirectory, "TumorClassifications");
			finalClassificationDir.mkdir();
		}
		
		printParams();
		if (errorFound) {
			log.error("Correct errors and restart.");
			System.exit(1);
		}
	}

	public void printParams() {
		boolean keyFound = apiKey!=null;
		log.info("""
				Run Parameters:
				\t-t TissuePrompt         {}
				\t-n TissueNodeCodesFile  {}
				\t-a TissueNodeCatalogDir {}
				\t-m Model                {}
				\t-c Content              {}
				\t-h Host                 {}
				\t-k API Key file         {}
				\t-j TumorJsonDir         {}
				\t-r ResultsDir           {}
				\t-s TimeOut              {}
				\t-e Temperature          {}
				\t-p Tries                {}
				\t-v Verbose              {}
				""",
				tissuePrompt, tissueCodeNodeCodes, tissueNodeCatalogDir, model, content, host, keyFound, tumorJsons[0].getParentFile(), resultsDirectory, timeOutInSeconds, temperature, numberAttempts, verbose);
		if (keyFound) log.info("WARNING: cloud LLM detected! Be certain PHI is not processed by this application!\n");
	}


	public void printDocs(){
		log.info("""
				**************************************************************************************
				**                         OncoTree Classifier : September 2026                     **
				**************************************************************************************
				This tool makes use of an LLM to classify tumors according to the OncoTree platform
				from MSK: https://oncotree.mskcc.org . Tumors are matched first to an OncoTree tissue
				and then to the best tumor classification node within that tissue.  Use the 
				TempusPathoPrinter to extract the required information from Tempus v3.3+ json test
				results. Start up an ollama server before running this tool or provide an API key.

				Options:
				  -t Path to the tissue classification prompt
				  -n Path to the tissue node codes file, e.g. tissueCodeNodeCodes.txt from the 
				       OncoTreePrinter
				  -a Path to the tissue node catalog folder, e.g. TissueNodeCatalog/ ditto
				  -j Path to a tumor json file or directory containing the same to classify
				  -r Path to a directory to write the results
				  
				  -m Model to run, defaults to gemma4:26b
				  -c Content to supply model, defaults to 35000
				  -h Host the ollama server is listening to, defaults to http://localhost:11434
				  -s Timeout in seconds for each query, defaults to 1200
				  -e Temperature, defaults to not setting it, 0.8
				  -k Use Ollama's cloud service with the API key in this txt file. This will set the
				       host to https://ollama.com . Make sure your -m model is cloud available.
				       Be certain no PHI is processed by this tool with cloud service.
				  -p Number of attempts for each classification, defaults to 5
				  -v Verbose
				  
				Example: java -jar OT_0.1.jar Classifier -j TumJsons2Classify/ -t OTP/tPrompt.txt 
				  -r Results -n OTP/tissueCodeNodeCodes.txt -a OTP/TissueNodeCatalog/ 
				  -k key.txt -p 2

				**************************************************************************************
				""");
	}



}
