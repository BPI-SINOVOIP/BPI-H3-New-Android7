#!/bin/bash -x

function usage() {
cat<<EOT
Tool:
	sign_ota.sh
Description:
	Use keys to signature the firmware

Usage:
	sign_ota_testboot.sh -i [old_ota.zip] -k [key] -o [secureboot_signed_ota.zip]
EOT
}

function assert() {
    if [ "$?" -ne 0 ]; then

        if [ $# -eq 1 ]; then
            echo $1
        fi
      rm -rf ${TEMP_DIR}
      exit 1
    fi
}

function getAbsPath() {
WORKDIR=$(pwd)
if [ ! -e "$1" ];then
    echo "$1 not exist, exit..."
    exit 1
fi


if [ -f $1 ];then
    cd $(dirname $1)
    eval "$2=$(pwd)/$(basename $1)"
    cd $WORKDIR
fi

if [ -d $1 ];then
    cd $1
    eval "$2=$(pwd)"
    cd $WORKDIR
fi
}


if [ $# -ne 6 ]; then
    usage
    exit
fi

. $(dirname ${BASH_SOURCE[0]})/tools/shflags

# define option, format:
#   'long option' 'default value' 'help message' 'short option'
DEFINE_string 'input' '' 'firmware to be signed' 'i'
DEFINE_string 'output' '' 'signed firmware' 'o'
DEFINE_string 'key' '' 'sign key' 'k'

# parse the command-line
FLAGS "$@" || exit $?
eval set -- "${FLAGS_ARGV}"

getAbsPath ${FLAGS_input} OLD_OTA
getAbsPath ${FLAGS_key} SING_KEY
getAbsPath $(dirname ${FLAGS_output}) SIGNED_OTA
SIGNED_OTA=${SIGNED_OTA}/$(basename ${FLAGS_output})

cd $(dirname ${BASH_SOURCE[0]})
ROOT_DIR=`pwd`
TOOLS_DIR="${ROOT_DIR}/tools"
export PATH=${TOOLS_DIR}:${PATH}
CONFIG_DIR="${ROOT_DIR}/config"
mkdir -p ${ROOT_DIR}/temp
TEMP_DIR="${ROOT_DIR}/temp"

mkdir -p ${TEMP_DIR}/key
cp ${ROOT_DIR}/../../../../../lichee/tools/pack/common/keys/* ${TEMP_DIR}/key/
cp $SING_KEY ${TEMP_DIR}/key/Trustkey.pem

openssl rsa -in ${TEMP_DIR}/key/Trustkey.pem -text -out ${TEMP_DIR}/key/Trustkey.bin
assert "covert key format fail"

mkdir -p ${TEMP_DIR}/img
cp ${ROOT_DIR}/../../../../../lichee/tools/pack/out/*.* ${TEMP_DIR}/img/
cp ${OUT}/boot.img ${TEMP_DIR}/img/boot.fex
cp ${OUT}/recovery.img ${TEMP_DIR}/img/recovery.fex

TOC0_IMG=${TEMP_DIR}/img/toc0.fex
TOC1_IMG=${TEMP_DIR}/img/toc1.fex

echo "Input ota package is at:"
echo ${OLD_OTA}
#unzip -o $OLD_OTA toc0.fex toc1.fex  -d ${TEMP_DIR}/img
#assert "unzip ota fail"

cp ${TOC0_IMG} ${TEMP_DIR}/img/toc0_bak.fex
cp ${TOC1_IMG} ${TEMP_DIR}/img/toc1_bak.fex

cd ${TEMP_DIR}/img/

dragonsecboot -toc0 ${ROOT_DIR}/config/dragon_toc.cfg  ${TEMP_DIR}/key
assert "sign toc0 fail"
update_toc0 toc0.fex sys_config.bin
assert "update toc0 fail"
dragonsecboot -toc1 ${ROOT_DIR}/config/dragon_toc.cfg  ${TEMP_DIR}/key  ${ROOT_DIR}/config/cnf_base.cnf
assert "sign toc1 fail"

#create secureboot-signed ota package
cp $OLD_OTA $SIGNED_OTA

zip -j $SIGNED_OTA $TOC0_IMG
zip -j $SIGNED_OTA $TOC1_IMG

$ROOT_DIR/signapk.sh $SIGNED_OTA temp_ota.zip
mv temp_ota.zip $SIGNED_OTA
rm -rf ${TEMP_DIR}
