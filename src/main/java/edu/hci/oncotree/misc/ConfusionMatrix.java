package edu.hci.oncotree.misc;


public class ConfusionMatrix {
	
	//fields
	private float TP = 0;
	private float FP = 0;
	private float FN = 0;
	private float TN = 0;
	private int noCall = 0;
	public static String toStringHeader = "TP\tFP\tFN\tTN\tNoCall\tTPR Recall Sensitivity\tFPR\tSpecificity\tPrecision PPV\tFDR\tAccuracy\tFScore";
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append((int)TP); sb.append("\t"); 
		sb.append((int)FP); sb.append("\t"); 
		sb.append((int)FN); sb.append("\t"); 
		sb.append((int)TN); sb.append("\t"); 
		sb.append(noCall); sb.append("\t"); 
		sb.append(getSensitivityRecallTpr()); sb.append("\t"); 
		sb.append(getFalsePositiveRate()); sb.append("\t");
		sb.append(getSpecificity()); sb.append("\t");
		sb.append(getPrecisionPpv()); sb.append("\t");
		sb.append(getFalseDiscoveryRate()); sb.append("\t");
		sb.append(getAccuracy()); sb.append("\t");
		sb.append(getFScore());
		return sb.toString();
	}
	public float getAccuracy() {
		return (TP + TN )/ (TP + TN + FP + FN);
	}
	public float getPrecisionPpv() {
		return TP / (TP + FP);
	}
	public float getSensitivityRecallTpr() {
		return TP / (TP + FN);
	}
	public float getSpecificity() {
		return TN / (TN + FP);
	}
	public float getFalseDiscoveryRate() {
		return FP / ( FP + TP );
	}
	public float getFalsePositiveRate() {
		return FP / (FP + TN);
	}
	public float getFScore() {
		return (float) Util.harmonicMean(new double[] {getPrecisionPpv(), getSensitivityRecallTpr()});
	}
	//from https://vitalflux.com/cohen-kappa-score-python-example-machine-learning/
	//public float getKappaScore() {
		//float N = TP + FP + FN + TN; 
		//float Po = (TP + TN) / N
	//}

	
	public void addTP() {
		TP++;
	}

	public void addFP() {
		FP++;
	}

	public void addFN() {
		FN++;
	}

	public void addTN() {
		TN++;
	}
	
	public void addNoCall() {
		noCall++;
	}

	

}
