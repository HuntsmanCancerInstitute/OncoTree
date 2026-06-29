This repository contains tools for classifying tumors according to the [MSK OncoTree (OT) platform](https://oncotree.mskcc.org/about). Proper tumor classification improves data interoperability, enables more reliable genomic and clinical outcome analyses, facilitates patient matching for biomarker-driven clinical trials, and ultimately supports the delivery of more precise, evidence-based treatment recommendations. See the [MSK OncoTree publication](https://ascopubs.org/doi/10.1200/CCI.20.00108) for details.

![OncoTree Tissues](https://github.com/HuntsmanCancerInstitute/OncoTree/blob/50af719de81beca0017d30a72966f444cf9a52e1/Resources/Images/oncoTree.png)

## Applications and Resources
1. **OncoTreeClassifier** - Makes use of a [Ollama](https://ollama.com) deployed LLM to match tumor information to the best OT Tissue and then the best OT Node within that Tissue.
2. **OncoTreeComparator** - Benchmarks the classifier codes against a truth set of 100 Tempus tumor reports.
3. **OncoTreePrinter** - Parses the OT data structure, pulls the referenced NCI Thesaurus codes, filters, formats, and outputs text for LLM prompt construction
4. **TempusPathoPrinter** - ([USeq Repo](https://github.com/HuntsmanCancerInstitute/USeq)) Parses Tempus v3.3+ json reports for information useful for the OncoTreeClassifier.
5. **Resources** - Reference files for the various applications. See the [oncoTree10MinPres2April2026.pptx](https://github.com/HuntsmanCancerInstitute/OncoTree/blob/ed78c9ac92069efe63e5181e71c17e7355558642/Resources/oncoTree10MinPres2April2026.pptx) for a project overview.

## Benchmarking with 100 Tempus Tumor Test Reports
![Benchmarking](https://github.com/HuntsmanCancerInstitute/OncoTree/blob/50af719de81beca0017d30a72966f444cf9a52e1/Resources/Images/benchmarking20April2026.png)

## Installation 
1. Install Java version 21 or later. Check it 'java -version'
2. Download the latest [USeq_XXX.zip](https://github.com/HuntsmanCancerInstitute/USeq/releases) release and unzip it. 
3. Download the latest [OncoTree OT_XXX.jar](https://github.com/HuntsmanCancerInstitute/OncoTree/releases)
4. Download the latest OncoTree Resource folder [OTResourcesXXX.zip](https://github.com/HuntsmanCancerInstitute/OncoTree/tree/master/Resources) and unzip it.
5. Obtain a [Ollama.com](https://ollama.com/) key and save it in a file called key.txt , alternatively see the [RunScripts](https://github.com/HuntsmanCancerInstitute/OncoTree/tree/master/Resources/RunScripts) folder for bash and snakemake workflow files for utilizing local nodes and a slurm cluster.

## Usage
**Convert your tumor information into a structured JSON file with these elements:**
```
{
   "icd_code_descriptions": "Malignant neoplasm of pancreas; Malignant neoplasm of pancreas, unspecified; Adenocarcinoma; Pancreas",
   "original_path_lab_diagnosis": "Adenocarcinoma",
   "test_order_id": "2ZN719381V",
   "sample_site": "Liver"
}
```
**DO NOT insert any PHI in these JSON files**

For Tempus v3.3+ JSON reports, use the USeq/TempusPathoPrinter to create these JSON files:
```
java -jar USeq_9.3.9/Apps/TempusPathoPrinter -j TempusReports -s ParsedReports \
-i OTResources29June2026/ICD/ICD-10_Diagnosis.txt \
-m OTResources29June2026/ICD/ICD_Morphology.txt \
-t OTResources29June2026/ICD/ICD_Topology.txt -r
```

**Execute the classifier using the Ollama.com service:**
```
java -jar OT_0.1.jar Classifier \
-k $(cat key.txt) \
-m gemma4:31b-cloud \
-c 24000 \
-t OTResources29June2026/promptTissue.txt \
-n OTResources29June2026/tissueCodeNodeCodes.txt \
-a OTResources29June2026/TissueNodeCatalog \
-e OTResources29June2026/TissueNodeExamples \
-j OTResources29June2026/TestJsons \
-r Results
```
Results for the TestJsons: 2ZN719381V.PANCREAS.PAAD.json  6VE87GH83V.BRAIN.HGGNOS.json  7T3IRL8Y85.MYELOID.RDD.json
 
   
**HCI Data Science Hackathon** - Many thanks to the 2025 'OncoTree LLM: AI Assisted Tumor Classification for Precision Oncology' first place team: Bradley Demarest, Gabby Fort, Chase Maughan, Jake Reed, and David Nix
