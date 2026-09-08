#!/bin/bash
# Author, david.nix@hci.utah.edu, 16 July 2026
set -e

content=35000
model="glm-5.1:cloud"
#model="gemma4:31b"
#model="deepseek-v4-pro:cloud"
#model="ministral-3:14b-cloud"
#model="qwen3.5:397b-cloud"

tumorJsonDir=~/TNRunner/OncoTree/ManualClassified/All100
prePrompt=~/TNRunner/OncoTree/OTResources6July2026/promptTissue.txt
codes=~/TNRunner/OncoTree/OTResources6July2026/tissueCodeNodeCodes.txt 
catalog=~/TNRunner/OncoTree/OTResources6July2026/TissueNodeCatalog/
jar=~/TNRunner/BioApps/OncoTree/OT_0.3.jar
keyFile=~/Scratch/OncoTree/GenericNode/FinalBenchmarking/Cloud/key.txt
resultsDir=${PWD##*/}

module load openjdk/23.0.1

java -jar -Xmx1G $jar Classifier \
-k $keyFile -m $model -c $content \
-t $prePrompt \
-n $codes \
-a $catalog \
-j $tumorJsonDir \
-r $resultsDir

echo COMPLETE
touch COMPLETE

