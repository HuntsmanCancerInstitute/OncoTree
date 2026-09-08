package edu.hci.oncotree.apps;

import edu.hci.oncotree.misc.Util;

/**Simple helper app to launch others using a common jar.*/
public class OT {

	public static void main(String[] args) {
		//any args?
		if (args.length ==0) {
			printDocs();
			System.exit(0);
		}
		
		String[] newArgs = new String[args.length - 1];
		System.arraycopy(args, 1, newArgs, 0, args.length - 1);
		String appLc = args[0].toLowerCase();
		if (appLc.contains("class")) new OncoTreeClassifier(newArgs);
		else if (appLc.contains("comp")) new OncoTreeComparator(newArgs);
		else if (appLc.contains("print")) new OncoTreePrinter(newArgs);
		else if (appLc.contains("path")) new PathLabCsvParser(newArgs);
		else {
			String s = Util.stringArrayToString(args, " ");
			Util.el("\nFailed to find one of the supported apps (Classifier, Comparator, Printer, or PathLabCsvParser) as the first argument in : "+s+"\n");
			System.exit(1);
		}
	}		

	 


	public static void printDocs(){
		Util.pl("""
				**************************************************************************************
				**                            OncoTree Tools : Sept 2026                            **
				**************************************************************************************
				Apps for working with and classifying tumors according to MSKCC's OncoTree platform:
				https://oncotree.mskcc.org and https://github.com/HuntsmanCancerInstitute/OncoTree

				Provide the name of the application you wish to run. Leave empty for the help menus.
				
				Classifier | Comparator | Printer | PathLabCsvParser
				  
				Example: java -jar OT.jar Classifier

				**************************************************************************************
				""");
	}



}
