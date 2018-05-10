#########################################################################
# File Name: test.sh
# Author: chenjuncong
# mail: chenjuncong@allwinnertech.com
# Created Time: 2017年06月07日 星期三 18时07分10秒
#########################################################################
#!/bin/bash

output_temp="ota_dir"
output_target_temp="targetfile_dir"
tools_dir="$ANDROID_BUILD_TOP/build/tools/verity/gen_file_verify_block.sh"
default_ota="tmp_ota.zip"

#ota package
unzip $1 -d $output_temp
#ota target file
unzip $2 -d $output_target_temp

#use gen_file_verify_block shell to get verity_block
$tools_dir "$output_target_temp/SYSTEM"

#substitute the verity_block file
ota_filename=`ls $1 |awk -F'/' {'print $NF'}`
filename=`echo $ota_filename | cut -d'.' -f1`

cp "$OUT/verity_block.img" verity_block.fex
zip -d $1 verity_block.fex
zip -ur $1 verity_block.fex

rm verity_block.fex
rm $output_temp -Rf
rm $output_target_temp -Rf
rm $ota_filename -Rf
