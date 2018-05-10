#!/bin/bash 

if [ -d "$1" ]; then
IMG=$1
else
	echo "Assume image is $OUT/system"
IMG=$OUT/system
fi
echo "Generate file verify block for file-system $IMG"
RUN_PATH=${ANDROID_BUILD_TOP}/build/tools/verity
FILE_SIG_TOOL="$RUN_PATH/file_sign"

echo " $IMG $FILE_SIG_TOOL"
#${FILE_SIG_TOOL} ${IMG} system sha256 ${OUT}/verity_block.img ${RUN_PATH}/rsa_key/rsa_key.pair  -d ${RUN_PATH} > ${RUN_PATH}/file.sig
${FILE_SIG_TOOL} ${IMG} system sha256 ${OUT}/verity_block.img ${RUN_PATH}/rsa_key/rsa_key.pair > /dev/null
#cp -rf $RUN_PATH/rsa_key/verity_key $OUT/root/
#cp -rf $RUN_PATH/rsa_key/rsa.pk $OUT/root/
exit 0

