This repository contains tools for classifying tumors according to the MSK OncoTree (OT) platform: https://oncotree.mskcc.org

**OncoTreeClassifier** - Makes use of a [ollama](https://ollama.com) deployed LLM to match tumor information to the best OT Tissue and then the best OT Node within that Tissue.

**OncoTreeComparator** - Benchmarks the classifier codes against a truth set of 100 Tempus tumor reports.

**OncoTreePrinter** - Parses the OT data structure, pulls the referenced NCI Thesaurus codes, filters, formats, and outputs text for LLM prompt construction

TempusPathoPrinter - ([USeq Repo](https://github.com/HuntsmanCancerInstitute/USeq)) Parses Tempus v3.3+ json reports for information useful for the OncoTreeClassifier.

Resources - Reference files for the various applications. See the oncoTree10MinPres2April2026.pptx for a project overview.
