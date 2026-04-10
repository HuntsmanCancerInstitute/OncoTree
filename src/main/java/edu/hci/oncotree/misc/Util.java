package edu.hci.oncotree.misc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Util {
	
	public static void pl(Object oj) {
		System.out.println(oj.toString());
	}
	public static void el(Object oj) {
		System.err.println(oj.toString());
	}
	
	/**Writes a String to disk. */
	public static boolean writeString(String data, File file) {
		try {
			PrintWriter out = new PrintWriter(new FileWriter(file));
			out.print(data);
			out.close();
			return true;
		} catch (IOException e) {
			System.out.println("Problem writing String to disk!");
			e.printStackTrace();
			return false;
		}
	}
	
	/**Converts a double ddd.dddddddd to a user determined number of decimal places right of the .  */
	public static String formatNumber(double num, int numberOfDecimalPlaces){
		NumberFormat f = NumberFormat.getNumberInstance();
		f.setMaximumFractionDigits(numberOfDecimalPlaces);
		return f.format(num);
	}
	
	/**Converts a hash to a String[].*/
	public static String[] treeSetToStringArray(TreeSet<String> hash){
		Iterator<String> it = hash.iterator();
		String[] s = new String[hash.size()];
		int counter =0;
		while (it.hasNext())s[counter++] = it.next();
		return s;
	}
	
	/**Loads a file's lines into a String[], will save blank lines. gz/zip OK*/
	public static String[] loadFile(File file){
		ArrayList<String> a = new ArrayList<String>();
		try{
			BufferedReader in = Util.fetchBufferedReader(file);
			String line;
			while ((line = in.readLine())!=null){
				line = line.trim();
				a.add(line);
			}
			in.close();
		}catch(Exception e){
			System.out.println("Prob loadFileInto String[]");
			e.printStackTrace();
		}
		String[] strings = new String[a.size()];
		a.toArray(strings);
		return strings;
	}

	
	/**Extracts the full path file names of all the files in a given directory with a given extension (ie txt or .txt).
	 * If the dirFile is a file and ends with the extension then it returns a File[] with File[0] the
	 * given directory. Returns null if nothing found. Case insensitive.*/
	public static File[] extractFiles(File dirOrFile, String extension){
		if (dirOrFile == null || dirOrFile.exists() == false) return null;
		File[] files = null;
		Pattern p = Pattern.compile(".*"+extension+"$", Pattern.CASE_INSENSITIVE);
		Matcher m;
		if (dirOrFile.isDirectory()){
			files = dirOrFile.listFiles();
			int num = files.length;
			ArrayList<File> fileAL = new ArrayList<File>();
			for (int i=0; i< num; i++)  {
				m= p.matcher(files[i].getName());
				if (m.matches()) fileAL.add(files[i]);
			}
			files = new File[fileAL.size()];
			fileAL.toArray(files);
		}
		else{
			m= p.matcher(dirOrFile.getName());
			if (m.matches()) {
				files=new File[1];
				files[0]= dirOrFile;
			}
		}
		if (files != null) Arrays.sort(files);
		return files;
	}
	
	/**Returns directories or null if none found. Not recursive.
	 * Skips those beginning with a period.*/
	public static File[] extractOnlyDirectories(File directory){
		if (directory.isDirectory() == false) return null;
		File[] fileNames = directory.listFiles();
		ArrayList<File> al = new ArrayList<File>();
		Pattern pat = Pattern.compile("^\\w+.*");
		Matcher mat; 
		for (int i=0; i< fileNames.length; i++)  {
			if (fileNames[i].isDirectory() == false) continue;
			mat = pat.matcher(fileNames[i].getName());
			if (mat.matches()) al.add(fileNames[i]);
		}
		//convert arraylist to file[]
		if (al.size() != 0){
			File[] files = new File[al.size()];
			al.toArray(files);
			Arrays.sort(files);
			return files;
		}
		else return new File[]{directory};
	}

	
	/**Returns a String[] given an ArrayList of Strings.*/
	public static String[] stringArrayListToStringArray(ArrayList<String> stringAL){
		String[] s = new String[stringAL.size()];
		stringAL.toArray(s);
		return s;
	}
	
	/**Returns a String separated by the separator given an ArrayList of String.*/
	public static String stringArrayListToString(ArrayList<String> stringAL, String separator){
		int len = stringAL.size();
		if (len==0) return "";
		if (len==1) return stringAL.get(0);
		StringBuffer sb = new StringBuffer(stringAL.get(0));
		for (int i=1; i<len; i++){
			sb.append(separator);
			sb.append(stringAL.get(i));
		}
		return sb.toString();
	}
	
	/**Returns a String separated by the delimiter.*/
	public static String stringArrayToString(String[] s, String separator){
		if (s==null) return "";
		int len = s.length;
		if (len==1) return s[0];
		if (len==0) return "";
		StringBuilder sb = new StringBuilder(s[0]);
		for (int i=1; i<len; i++){
			sb.append(separator);
			sb.append(s[i]);
		}
		return sb.toString();
	}

	/**Prints message to screen, then exits.*/
	public static void printErrAndExit (String message){
		System.err.println (message);
		System.exit(1);
	}
	
	/**Loads a file's lines into a String, will save blank lines. gz/zip OK.
	 * @author david.nix@hci.utah.edu*/
	public static String loadFile(File file, String seperator, boolean trimLeadingTrailing){
		StringBuilder sb = new StringBuilder();
		try{
			BufferedReader in = fetchBufferedReader(file);
			String line;
			while ((line = in.readLine())!=null){
				if (trimLeadingTrailing) line = line.trim();
				sb.append(line);
				sb.append(seperator);
			}
			in.close();
		}catch(Exception e){
			System.out.println("Prob loadFileInto String "+file);
			e.printStackTrace();
		}
		return sb.toString();
	}
	
	/**Returns a gz zip or straight file reader on the file based on it's extension.
	 * @author david.nix@hci.utah.edu*/
	public static BufferedReader fetchBufferedReader( File txtFile) throws IOException{
		BufferedReader in;
		String name = txtFile.getName().toLowerCase();
		if (name.endsWith(".zip")) {
			ZipFile zf = new ZipFile(txtFile);
			ZipEntry ze = (ZipEntry) zf.entries().nextElement();
			in = new BufferedReader(new InputStreamReader(zf.getInputStream(ze)));
		}
		else if (name.endsWith(".gz")) {
			in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(txtFile))));
		}
		else in = new BufferedReader (new FileReader (txtFile));
		return in;
	}

}
