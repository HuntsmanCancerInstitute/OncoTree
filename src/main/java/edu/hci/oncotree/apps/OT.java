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
		
		if (args[0].contains("Classifier")) new OncoTreeClassifier(newArgs);
		else if (args[0].contains("Comparator")) new OncoTreeComparator(newArgs);
		else if (args[0].contains("Printer")) new OncoTreePrinter(newArgs);
		else {
			String s = Util.stringArrayToString(args, " ");
			Util.el("\nFailed to find one of the supported apps (Classifier, Comparator, or Printer) as the first argument in : "+s+"\n");
		}
	}		

	 


	public static void printDocs(){
		Util.pl("""
				**************************************************************************************
				**                            OncoTree Tools : April 2026                           **
				**************************************************************************************
				Apps for working, with and classifying tumors according to, MSKCC's OncoTree platform:
				https://oncotree.mskcc.org and https://github.com/HuntsmanCancerInstitute/OncoTree

				Provide the name of the application you wish to run. Leave empty for the help menus.
				
				Classifier | Comparator | Printer
				  
				Example: java -jar OT.jar Classifier

				**************************************************************************************
				""");
	}



}
