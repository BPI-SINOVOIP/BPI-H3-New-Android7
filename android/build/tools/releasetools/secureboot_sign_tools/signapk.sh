#########################################################################
# File Name: signapk.sh
# Author: chenjuncong
# mail: chenjuncong@allwinnertech.com
# Created Time: 2017年06月16日 星期五 13时48分23秒
#########################################################################
#!/bin/bash

function usage(){
cat<<EOT
    Usage:
        signapk.sh [old_ota.zip] [new_ota.zip]
EOT
}

if [ $# -ne 2 ];then
    usage
    exit
fi

java -Xmx2048m -Djava.library.path=$ANDROID_BUILD_TOP/out/host/linux-x86/lib64 -jar $ANDROID_BUILD_TOP/out/host/linux-x86/framework/signapk.jar -w $ANDROID_BUILD_TOP/build/target/product/security/testkey.x509.pem $ANDROID_BUILD_TOP/build/target/product/security/testkey.pk8 $1 $2
echo "sign ota package finished"
