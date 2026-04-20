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
	private String model = "granite4:latest"; //"gemma3:12b";
	private String host = "http://localhost:11434";
	private int content = 22000;
	private boolean verbose = false;
	private int timeOutInSeconds = 600; // 10 min
	private File tissueCodeNodeCodes = null;
	private File tissueNodeCatalogDir = null;
	private File tissueNodeExampleDir = null;

	//internal
	private Logger log = null;
	private String tissuePrePrompt = null;
	private TreeMap<String, ClassifiedTumor> testIdClasTum = new TreeMap<String, ClassifiedTumor>();
	private Ollama ollama = null;
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
			tissueNodePromptBuilder = new TissueNodePromptBuilder(tissueCodeNodeCodes, tissueNodeCatalogDir, tissueNodeExampleDir);
			allNodeCodes = tissueNodePromptBuilder.getAllNodeCodes();
			tissueCodes = tissueNodePromptBuilder.getTissueCodeNodeCodes();
					
			//load any prior results
			loadPriorTissueClassificationResults();
			loadPriorNodeClassificationResults();

			//connect to the ollama server
			connectToOllamaServer();

			//classify the tumor jsons for tissue
			classifyTumorTissues();

			//classify the tumor jsons for best tissue node
			classifyTumorNodes();
			
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
				# Tumor Samples	{}
				# Tissue NONE	{}
				# Failed Tissue	{}
				# Failed Node	{}
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
			log.debug("\t"+ct.getTestOrderId());
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

	private void classifyTumorTissues() throws Exception {
		log.info("\nClassifying tumor tissues...");
		
		for (ClassifiedTumor ct: testIdClasTum.values()) {
			log.debug("\t"+ct.getTestOrderId());
			
			//already processed?
			if (processedTissueTestIds.containsKey(ct.getTestOrderId())) {
				ct.setTissueClassification(processedTissueTestIds.get(ct.getTestOrderId()));
				log.info("\t"+ct.getTestOrderId()+"\t"+ct.getOncoTreeTissueCode()+"\ttissue classified, skipping");
				checkTissueClassification(ct);
				continue;
			}
			
			String result = callOllama(ct, tissuePrePrompt);
			log.debug("Response\n"+result);
			
			//look for issues and trim the result to just {xxxxx}
			String parsed = parseJsonResult(result);
			log.debug("Parsed\n"+parsed);
			if (parsed == null) throw new Exception("Failed to parse a json response object from \n"+ result);
			
			JSONObject jo = new JSONObject(parsed);
			ct.setTissueClassification(jo);
			log.info("\t"+ct.getTestOrderId()+ "\t"+ct.getOncoTreeTissueCode());
			checkTissueClassification(ct);
			
			//write out parsed result
			ct.saveTissueJson(tissueJsonDir);
		}
	}
	
	private void checkTissueClassification(ClassifiedTumor ct) {
		String tc = ct.getOncoTreeTissueCode();
		//is it NONE? This is OK
		if (tc.equals("NONE")) {
			numNoneTissueClassifications++;
			ct.setSkipNodeClassification(true);
			ct.setOncoTreeNodeCode("NONE");
			ct.setTissueClassificationOK(true);
			ct.setNodeClassificationOK(true);
			log.warn("WARNING: NONE tissue code, manually classify "+ct.getTestOrderId());
		}
		//is it a legitimate OT tissue code?
		else if (tissueCodes.containsKey(tc)==false) {
			numFailedTissueClassifications++;
			ct.setSkipNodeClassification(true);
			ct.setTissueClassificationOK(false);
			ct.setNodeClassificationOK(false);
			log.error("ERROR: tissue code "+tc + " is not a valid OT Tissue Code, check the tissue classification for "+ct.getTestOrderId());
		}
		else ct.setTissueClassificationOK(true);
	}

	private void classifyTumorNodes() throws Exception {
		log.info("\nClassifying tumor nodes...");
		
		for (ClassifiedTumor ct: testIdClasTum.values()) {
			log.debug("\t"+ct.getTestOrderId());
			
			//check tissue code
			String tissueCode = ct.getOncoTreeTissueCode();
			if (ct.isSkipNodeClassification()) {
				log.info("\tSkipping node classification for "+ct.getTestOrderId()+", see messages above.");
				continue;
			}
			
			//already processed?
			if (processedNodeTestIds.containsKey(ct.getTestOrderId())) {
				ct.setNodeClassification(processedNodeTestIds.get(ct.getTestOrderId()));
				log.info("\t"+ct.getTestOrderId()+"\t"+ct.getOncoTreeNodeCode()+"\tnode classified, skipping");
				checkNodeCode(ct);
				continue;
			}
			
			String nodePrompt = tissueNodePromptBuilder.fetchPrompt(tissueCode);
			
			//some tissues haven't been seen before so have no examples, create and add it then rerun.
			if (nodePrompt == null) {
				log.error("ERROR: Failed to fetch a node prompt for "+tissueCode+", skipping "+ct.getTestOrderId()+ ", check "+tissueNodeExampleDir+" and add an entry!");
				numFailedNodeClassifications++;
				ct.setNodeClassificationOK(false);
				continue;
			}
			
			String result = callOllama(ct, nodePrompt);
			log.debug("Node Response\n"+result);
			
			//look for issues and trim the result to just {xxxxx}
			String parsed = parseJsonResult(result);
			log.debug("Node Parsed\n"+parsed);
			if (parsed == null) throw new Exception("Failed to parse a json response object from \n"+ result);
			
			JSONObject jo = new JSONObject(parsed);
			ct.setNodeClassification(jo);
			log.info("\t"+ct.getTestOrderId()+ "\t"+ct.getOncoTreeNodeCode());
			checkNodeCode(ct);
			
			//write out parsed result
			ct.saveNodeJson(nodeJsonDir);
		}
	}

	private void checkNodeCode(ClassifiedTumor ct) {
		//check if it is legitimate
		if (allNodeCodes.contains(ct.getOncoTreeNodeCode())==false) {
			numFailedNodeClassifications++;
			log.error("ERROR: Node Code "+ct.getOncoTreeNodeCode()+" is not found in OncoTree, see "+ct.getTestOrderId());
			ct.setNodeClassificationOK(false);
		}
		else ct.setNodeClassificationOK(true);
	}

	private static Pattern forwardBracket = Pattern.compile("\\{", Pattern.DOTALL);
	private static Pattern reverseBracket = Pattern.compile("\\}", Pattern.DOTALL);
	private static Pattern brackets = Pattern.compile(".*(\\{.+\\}).*", Pattern.DOTALL);
	private String parseJsonResult(String result) {
		String parsed = null;
		
		Matcher mat = forwardBracket.matcher(result);
		int numForward = 0;
		while (mat.find()) numForward++;
		if (numForward !=1) return null;
		
		int numReverse = 0;
		mat = reverseBracket.matcher(result);
		while (mat.find()) numReverse++;
		if (numReverse !=1) return null;
		
		mat = brackets.matcher(result);
		if (mat.matches()) return mat.group(1);

		return parsed;
	}

	private String callOllama(ClassifiedTumor tumor, String prompt) throws Exception {
		Options options = new OptionsBuilder()
				.setNumCtx(content)  
				.build();

		String classificationRequest = "PLEASE CLASSIFY THIS TUMOR:\n"+ tumor.getTumorInfo().toString(3);

		log.debug(prompt+classificationRequest);
		
		// SYSTEM is the background info, USER is the specific request
		OllamaChatRequest request = OllamaChatRequest.builder()
				.withModel(model)
				.withOptions(options)
				.withMessage(OllamaChatMessageRole.SYSTEM, prompt)
				.withMessage(OllamaChatMessageRole.USER, classificationRequest)
				.build();
		
		// Pass null as the token handler for non-streaming (blocking) response
		OllamaChatResult result = ollama.chat(request, null);

		checkPromptFit(result);

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

		// Verify the server is reachable at startup
		if (!ollama.ping()) throw new Exception("Cannot reach Ollama server at " + host + ". Make sure 'ollama serve' is running and the host URL is correctly set.");
		log.debug("Connected to Ollama at " + host);
	}

	private void loadTumorJsons() {
		log.info("Loading tumor json files...");
		for (int i=0; i< tumorJsons.length; i++) {
			log.debug("\t"+ tumorJsons[i]);
			ClassifiedTumor ct = new ClassifiedTumor(tumorJsons[i]);
			testIdClasTum.put(ct.getTestOrderId(), ct);
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
					case 'm': model = args[++i]; break;
					case 'h': host = args[++i]; break;
					case 's': timeOutInSeconds = Integer.parseInt(args[++i]); break;
					case 'n': tissueCodeNodeCodes = new File(args[++i]); break;
					case 'a': tissueNodeCatalogDir = new File(args[++i]); break;
					case 'e': tissueNodeExampleDir = new File(args[++i]); break;
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
		if (resultsDirectory == null) {
			log.error("ERROR: Please provide a path to a directory for saving the results\n");
			errorFound = true;
		}
		else resultsDirectory.mkdirs();
		tissueJsonDir = new File (resultsDirectory, "TissueClassified");
		tissueJsonDir.mkdir();
		nodeJsonDir = new File (resultsDirectory, "NodeClassified");
		nodeJsonDir.mkdir();
		finalClassificationDir = new File (resultsDirectory, "TumorClassifications");
		finalClassificationDir.mkdir();
		
		if (tissueCodeNodeCodes == null || tissueCodeNodeCodes.exists()== false) {
			log.error("ERROR: Cannot find your tissue code node codes tissueCodeNodeCodes.txt file, "+tissueCodeNodeCodes+"\n");
			errorFound = true;
		}
		if (tissueNodeCatalogDir == null || tissueNodeCatalogDir.exists()== false) {
			log.error("ERROR: Cannot find your tissue node catalog directory, "+tissueNodeCatalogDir+"\n");
			errorFound = true;
		}
		if (tissueNodeExampleDir == null || tissueNodeExampleDir.exists()== false) {
			log.error("ERROR: Cannot find your tissue node example directory, "+tissueNodeExampleDir+"\n");
			errorFound = true;
		}
		tumorJsons = Util.extractFiles(tumorJsonDir, ".json");
		if (tumorJsons == null || tumorJsons.length ==0) {
			log.error("ERROR: Failed to find any xxx.json tumor files in "+tumorJsonDir+"\n");
			errorFound = true;
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
				\t-t TissuePrompt         {}
				\t-n TissueNodeCodesFile  {}
				\t-a TissueNodeCatalogDir {}
				\t-e TissueNodeExampleDir {}
				\t-m Model                {}
				\t-c Content              {}
				\t-h Host                 {}
				\t-j TumorJsonDir         {}
				\t-r ResultsDir           {}
				\t-s TimeOut              {}
				\t-v Verbose              {}
				""",
				tissuePrompt, tissueCodeNodeCodes, tissueNodeCatalogDir, tissueNodeExampleDir, model, content, host, tumorJsons[0].getParentFile(), resultsDirectory, timeOutInSeconds, verbose);
	}


	public void printDocs(){
		log.info("""
				**************************************************************************************
				**                          OncoTree Classifier : April 2026                        **
				**************************************************************************************
				This tool makes use of an LLM to classify tumors according to the OncoTree platform
				from MSK: https://oncotree.mskcc.org . Tumors are matched first to an OncoTree tissue
				and then to the best classification node within that tissue.  Use the 
				TempusPathoPrinter to extract the required information from Tempus v3.3+ json test
				results. Start up an ollama server before running this tool. 

				Options:
				  -t Path to the tissue classification prompt
				  -n Path to the tissue node codes file, e.g. tissueCodeNodeCodes.txt from the 
				       OncoTreePrinter
				  -a Path to the tissue node catalog folder, e.g. TissueNodeCatalog/ ditto
				  -e Path to the tissue node example folder, e.g. TissueNodeExamples/ 
				  -j Path to a tumor json file or directory containing the same to classify
				  -r Path to a directory to write the results
				  
				  -m Model to run, defaults to gemma3:12b
				  -c Content to supply model, defaults to 22000
				  -h Host the ollama server is listening to, defaults to http://localhost:11434
				  -s Timeout in seconds for each query, defaults to 1200
				  
				Example: java -jar OncoTreeLLMClassifier.jar -t promptKP.txt -j TumJsons4Class
				  -r Results -n OTP/tissueCodeNodeCodes.txt -a OTP/TissueNodeCatalog/ -e 
				  OTP/TissueNodeExamples/

				**************************************************************************************
				""");
	}



}
