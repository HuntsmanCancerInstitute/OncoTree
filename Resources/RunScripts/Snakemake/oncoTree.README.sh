#!/bin/bash
#SBATCH --account=hci-rw
#SBATCH --partition=hci-rw
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=64
#SBATCH --mem=255000M
#SBATCH --exclusive
#SBATCH -t 10:00:00

# 22 April 2026
# David.Nix@HCI.Utah.Edu
# Huntsman Cancer Institute

# This workflow parses tumor information from Tempus v3.3+ json DNA sequencing reports (panel or exome) and then uses a LLM in Ollama to 
#   classify the tumor according to the OncoTree platform from MSKCC, see: https://oncotree.mskcc.org and 
#   https://github.com/HuntsmanCancerInstitute/OncoTree .  It must be run on a very large server that can support Gemma4:26b with a content of 24000
#   Required modules include snakemake, ollama, and openjdk/23.0.1 or newer.

######## For each classification job #########

# 1) Create a job folder named as you would like the analysis name to appear, this will be prepended onto all files, no spaces, change into it.

# 2) Create a folder called TempusReports and copy or soft link in the Tempus v3.3+ json reports for DNA sequencing you wish to classify. No PHI will be parsed or included in any log output.

# 3) Copy or soft link into the job folder the oncoTree.README.sh, oncoTree.sm, and oncoTree.yaml files.

# 4) Check the oncoTree.yaml file and adjust the parameters to match your environment..
 
# 5) Launch the oncoTree.README.sh via sbatch or run it on the local server, e.g. bash ./*README.sh

# 6) If the run fails, fix the issue and restart.  Snakemake should pick up where it left off. If needed, try deleting the .snakemake dir first to clear any locked files.


######## No need to edit the following #########

start=$(date +'%s'); rm -f COMPLETE FAILED QUEUED
echo -e "\n---------- Starting -------- $((($(date +'%s') - $start)/60)) min"

module load snakemake

jobName=${PWD##*/}
allThreads=$(nproc)

# Run Snakemake
module load snakemake
snakemake -p --snakefile oncoTree.sm --cores $allThreads  --configfile oncoTree.yaml --config jobName=$jobName allThreads=$allThreads 

# Check if complete
if [ -f COMPLETE ];
then
echo -e "\n---------- Complete! -------- $((($(date +'%s') - $start)/60)) min total"
  mkdir -p RunScripts
  mv -f oncoTree.* RunScripts/ &> /dev/null || true
  mv slurm* Logs/ &> /dev/null || true
  rm -rf STARTED RESTART* QUEUED .snakemake
else
  echo -e "\n---------- Failed! -------- $((($(date +'%s') - $start)/60)) min total"
  touch FAILED
fi
