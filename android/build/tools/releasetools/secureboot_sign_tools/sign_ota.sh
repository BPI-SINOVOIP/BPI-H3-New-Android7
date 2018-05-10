#!/bin/bash -x

function usage() {
cat<<EOT
Tool:
	sign_ota.sh
Description:
	Use keys to signature the firmware

Usage:
	sign_ota.sh -i [old_ota.zip] -k [key] -o [secureboot_signed_ota.zip]
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
getAbsPath ${FLAGS_key} SIGN_KEY
getAbsPath $(dirname ${FLAGS_output}) OTA_PATH
SIGNED_OTA=${OTA_PATH}/$(basename ${FLAGS_output})

cd $(dirname ${BASH_SOURCE[0]})
ROOT_DIR=`pwd`
TOOLS_DIR="${ROOT_DIR}/tools"
export PATH=${TOOLS_DIR}:${PATH}
CONFIG_DIR="${ROOT_DIR}/config"

rm -rf ${ROOT_DIR}/temp
mkdir -p ${ROOT_DIR}/temp
TEMP_DIR="${ROOT_DIR}/temp"

#create temp directory
mkdir -p ${TEMP_DIR}/key
mkdir -p ${TEMP_DIR}/img

#sync keys&&dragon_toc.cfg from lichee/tools/pack/common/keys
cp $ANDROID_BUILD_TOP/../lichee/tools/pack/common/keys/* ${TEMP_DIR}/key/
cp $ANDROID_BUILD_TOP/../lichee/tools/pack/out/dragon_toc.cfg ${ROOT_DIR}/config/dragon_toc.cfg

if [ -f $SIGN_KEY ]; then
	cp $SIGN_KEY ${TEMP_DIR}/key/Trustkey.pem
fi

openssl rsa -in ${TEMP_DIR}/key/Trustkey.pem -text -out ${TEMP_DIR}/key/Trustkey.bin
assert "covert key format fail"

# sync *.fex to temp/img
cp ${ROOT_DIR}/../../../../../lichee/tools/pack/out/* ${TEMP_DIR}/img/
TOC0_IMG=${TEMP_DIR}/img/toc0.fex
TOC1_IMG=${TEMP_DIR}/img/toc1.fex
BOOT_IMG=${TEMP_DIR}/img/boot.fex
RECOVERY_IMG=${TEMP_DIR}/img/recovery.fex


echo "Input ota package is at:"
echo ${OLD_OTA}
unzip -o $OLD_OTA boot.img toc0.fex toc1.fex system/etc/recovery-resource.dat recovery/bin/install-recovery.sh recovery/recovery-from-boot.p -d ${TEMP_DIR}/img
assert "unzip ota fail"

cp ${TOC0_IMG} ${TEMP_DIR}/img/toc0_bak.fex
cp ${TOC1_IMG} ${TEMP_DIR}/img/toc1_bak.fex
cp ${TEMP_DIR}/img/boot.img ${TEMP_DIR}/img/boot.fex
cp ${ROOT_DIR}/tools/applypatch ${TEMP_DIR}/img/
chmod 755 ${TEMP_DIR}/img/applypatch
APPLYPATCH=${TEMP_DIR}/img/applypatch.sh
cp ${TEMP_DIR}/img/recovery/bin/install-recovery.sh ${APPLYPATCH}
chmod 755 ${APPLYPATCH}

sed -i -e '2d' -e '4,$d' $APPLYPATCH
sed -i -r '2s/\&\&.*$//g' $APPLYPATCH
sed -i -r '1s/\/system\/bin\/sh/\/bin\/bash/g' $APPLYPATCH
sed -i -r '2s/EMMC:\S+boot\S+/boot\.img/g' $APPLYPATCH
sed -i -r '2s/EMMC:\S+recover\S+/-/g' $APPLYPATCH
sed -i -r '2s/\/system/system/g' $APPLYPATCH
sed -i -r '2s/system\/recovery/recovery\/recovery/g' $APPLYPATCH
sed -i -r '2s/applypatch/\.\/applypatch/g' $APPLYPATCH

cd ${TEMP_DIR}/img/
$APPLYPATCH
mv ${TEMP_DIR}/img/boot.img ${TEMP_DIR}/img/recovery.fex

#aw special flow begin to sign toc0 && toc1
$ANDROID_BUILD_TOP/../lichee/tools/pack/pctools/linux/openssl/dragonsecboot -toc0 ${ROOT_DIR}/config/dragon_toc.cfg  ${TEMP_DIR}/key
assert "sign toc0 fail"

$ANDROID_BUILD_TOP/../lichee/tools/pack/pctools/linux/mod_update/update_toc0 toc0.fex sys_config.bin
assert "update toc0 fail"

$ANDROID_BUILD_TOP/../lichee/tools/pack/pctools/linux/openssl/dragonsecboot -toc1 ${ROOT_DIR}/config/dragon_toc.cfg  ${TEMP_DIR}/key  ${ROOT_DIR}/config/cnf_base.cnf
assert "sign toc1 fail"
#aw special flow to sign toc0 && toc1 end

sigbootimg --image boot.fex --cert toc1/cert/boot.der --output boot.img
sigbootimg --image recovery.fex --cert toc1/cert/recovery.der --output recovery.img

imgdiff -b system/etc/recovery-resource.dat boot.img recovery.img recovery-from-boot.p
mv recovery-from-boot.p recovery/recovery-from-boot.p
sha1sum boot.img > sedtmp
BOOTIMG_SHA=$(sed -r 's/\s+\w+\.\w+//g' sedtmp)
sha1sum recovery.img > sedtmp
RECOVERYIMG_SHA=$(sed -r 's/\s+\w+\.\w+//g' sedtmp)
du -b boot.img > sedtmp
BOOTIMG_SIZE=$(sed -r 's/\s+\w+\.\w+//g' sedtmp)
du -b recovery.img > sedtmp
RECOVERYIMG_SIZE=$(sed -r 's/\s+\w+\.\w+//g' sedtmp)
echo "boot:${BOOTIMG_SHA}"
echo "boot size:${BOOTIMG_SIZE}"
echo "recovery:${RECOVERYIMG_SHA}"
echo "recovery size:${RECOVERYIMG_SIZE}"
cp recovery/bin/install-recovery.sh recovery/bin/install-recovery.sh.bk
sed -i -r "2s/recovery:[0-9]+:/recovery:${RECOVERYIMG_SIZE}:/g" recovery/bin/install-recovery.sh
sed -i -r "2s/:[0-9a-fA-F]+;/:${RECOVERYIMG_SHA};/g" recovery/bin/install-recovery.sh
sed -i -r "3s/boot:[0-9]+:/boot:${BOOTIMG_SIZE}:/g" recovery/bin/install-recovery.sh
sed -i -r "3s/:[0-9a-fA-F]+\ /:${BOOTIMG_SHA}\ /g" recovery/bin/install-recovery.sh
sed -i -r "3s/recovery\ [0-9a-fA-F]+/recovery\ ${RECOVERYIMG_SHA}/g" recovery/bin/install-recovery.sh
sed -i -r "3s/\ [0-9]+\ /\ ${RECOVERYIMG_SIZE}\ /g" recovery/bin/install-recovery.sh
sed -i -r "3s/\ [0-9a-fA-F]+:/\ ${BOOTIMG_SHA}:/g" recovery/bin/install-recovery.sh

#create secureboot-signed ota package
cp $OLD_OTA $SIGNED_OTA
zip -d $SIGNED_OTA system/bin/install-recovery.sh
zip -d $SIGNED_OTA system/recovery-from-boot.p
rm -rf system
mv recovery system
zip $SIGNED_OTA system/bin/install-recovery.sh
zip $SIGNED_OTA system/recovery-from-boot.p
zip -j $SIGNED_OTA boot.img
zip -j $SIGNED_OTA $TOC0_IMG
zip -j $SIGNED_OTA $TOC1_IMG

unzip $SIGNED_OTA -d signed_ota_package >/dev/null
verify_tools="$ANDROID_BUILD_TOP/build/tools/verity/gen_file_verify_block.sh"


target_file=$(ls -Art $OUT/obj/PACKAGING/target_files_intermediates | grep '.zip'|tail -n 1)
target_dir=`basename "$target_file" .zip`

echo "target_dir === ${target_dir}"

#backup install-recovery.sh and recovery-from-boot.p
cp $OUT/obj/PACKAGING/target_files_intermediates/${target_dir}/SYSTEM/bin/install-recovery.sh /tmp/
cp $OUT/obj/PACKAGING/target_files_intermediates/${target_dir}/SYSTEM/recovery-from-boot.p /tmp/

#aw fivm special flow begin
cp system/bin/install-recovery.sh $OUT/obj/PACKAGING/target_files_intermediates/${target_dir}/SYSTEM/bin/install-recovery.sh
cp system/recovery-from-boot.p $OUT/obj/PACKAGING/target_files_intermediates/${target_dir}/SYSTEM/recovery-from-boot.p
$verify_tools $OUT/obj/PACKAGING/target_files_intermediates/${target_dir}/SYSTEM
#aw fivm special flow end

#use verify_tools to target files
zip -d $SIGNED_OTA verity_block.img
#cp "$OUT/verity_block.img" verity_block.fex
cp "$OUT/verity_block.img" verity_block.img
#zip -ur $SIGNED_OTA verity_block.fex
zip -ur $SIGNED_OTA verity_block.img
rm verity_block.img
rm signed_ota_package -Rf

$ROOT_DIR/signapk.sh $SIGNED_OTA temp_ota.zip
mv temp_ota.zip ${SIGNED_OTA}
rm -rf ${TEMP_DIR}
