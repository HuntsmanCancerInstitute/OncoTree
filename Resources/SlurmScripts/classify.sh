#!/bin/bash
#SBATCH --account=hci-rw
#SBATCH --partition=hci-rw
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=64
#SBATCH --mem=255000M
#SBATCH --exclusive
#SBATCH -t 24:00:00

# Author, david.nix@hci.utah.edu, 20 Aug 2026

#exit on any app error and save start time
set -e; start=$(date +'%s'); rm -f COMPLETE

echo -e "\n---------- Initializing -------- $((($(date +'%s') - $start)/60)) min"

# Set for the run
resultsDir=Gemma4-26b

content=24000
model="gemma4:26b"
tumorJsonDir=/scratch/general/pe-nfs1/u0028003/OncoTree/OncoTreeClassifier/Tumors2Classify
prePrompt=/uufs/chpc.utah.edu/common/HIPAA/u0028003/Scratch/OncoTree/Prompts/promptKPNoFinalCmd.txt
codes=/uufs/chpc.utah.edu/common/HIPAA/u0028003/Scratch/OncoTree/NodeResources/tissueCodeNodeCodes.txt
catalog=/uufs/chpc.utah.edu/common/HIPAA/u0028003/Scratch/OncoTree/NodeResources/TissueNodeCatalog
examples=/uufs/chpc.utah.edu/common/HIPAA/u0028003/Scratch/OncoTree/NodeResources/TissueNodeExamples
jar=/scratch/general/pe-nfs1/u0028003/OncoTree/Jars/OncoTreeClassifier_0.1.jar

echo -e "\n---------- Starting Ollama Server -------- $((($(date +'%s') - $start)/60)) min"

allThreads=$(nproc)
allRam=$(expr `free -g | grep -oP '\d+' | head -n 1` - 2)
echo Parmams:
echo -e "\tCPU "$allThreads
echo -e "\tRAM "$allRam

module load ollama
export OLLAMA_MODELS=/uufs/chpc.utah.edu/common/PE/hci-bioinformatics1/Nix/Ollama
export OLLAMA_NUM_PARALLEL=$(nproc)
export OLLAMA_CONTEXT_LENGTH=$content
export OLPORT=`ruby -e 'require "socket"; puts Addrinfo.tcp("", 0).bind {|s| s.local_address.ip_port }'`
export OLLAMA_HOST=127.0.0.1:$OLPORT
export OLLAMA_BASE_URL="http://127.0.0.1:$OLPORT"
echo

# Start up the server and wait
ollama serve &> ollama.server.log &
SERVE_PID=$!
sleep 5

ollama pull $model

echo -e "\n---------- Starting Classifier -------- $((($(date +'%s') - $start)/60)) min"

# Run the classifier
module load openjdk/23.0.1

java -jar -Xmx1G $jar \
-t $prePrompt \
-j $tumorJsonDir \
-r $resultsDir \
-h $OLLAMA_BASE_URL \
-m $model \
-n $codes \
-a $catalog \
-e $examples \
-c $content \
-s 2400 

# Kill the ollama server
kill $SERVE_PID

touch COMPLETE

echo -e "\n---------- Complete! -------- $((($(date +'%s') - $start)/60)) min total"
