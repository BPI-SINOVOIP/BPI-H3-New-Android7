#!/bin/bash -e

LAST_TF=""
OUTPUT_DIR="output"
SIGN_DIR=""
PRODUCT=""

function usage() {
cat<<EOT
Tool:
build.sh
Description:
    build firmware, targetfile, ota full package

Usage:
    build.sh -o [OUTPUT_DIR] [ -s [KEY_DIR] -i [LAST_TF] ]
        -p [PRODUCT] :  necessary, product name
        -o [OUTPUT_DIR] : necessary, dir for output
        -s [KEY_DIR] : option, sign targetfile, not use develop key
        -i [LAST_TF] : option, build increase ota package base on LAST_TF
EOT
}

function printBuiltPara() {
    echo "---------------------------------------"
    echo "BuildPara:"
    echo "build product =${PRODUCT}"
    if [ ! "x${OUTPUT_DIR}" = "x" ]; then
        echo "output dir = ${OUTPUT_DIR}"
    fi
    if [ ! "x${SIGN_DIR}" = "x" ]; then
        echo "key dir = ${SIGN_DIR}"
    fi
    if [ ! "x${LAST_TF}" = "x" ]; then
        echo "last targetfile = ${LAST_TF}"
    fi
    echo "---------------------------------------"
}

function getAbsPath() {
_WORKDIR=$(pwd)
if [ ! -e "$1" ];then
    echo "$1 not exist, exit..."
    exit 1
fi


if [ -f $1 ];then
    cd $(dirname $1)
    eval "$2=$(pwd)/$(basename $1)"
    cd $_WORKDIR
fi

if [ -d $1 ];then
    cd $1
    eval "$2=$(pwd)"
    cd $_WORKDIR
fi
}


function build() {
source ${WORKDIR}/build/envsetup.sh
lunch ${PRODUCT}
extract-bsp
make installclean
make -j16
rm -rf out/dist
set +e
get_uboot
set -e
make dist -j16

CURRENT_TIME=$(date +%Y%m%d%H%M%S)
if [ ! "x${SIGN_DIR}" = "x" ]; then
BUILD_TF=$ANDROID_BUILD_TOP/out/dist/signed-target-files-$CURRENT_TIME.zip
$ANDROID_BUILD_TOP/build/tools/releasetools/sign_target_files_apks -d ${SIGN_DIR} $ANDROID_BUILD_TOP/out/dist/$TARGET_PRODUCT-target_files-$(date +%Y%m%d).zip  ${BUILD_TF}
else
BUILD_TF=out/dist/$TARGET_PRODUCT-target_files-$(date +%Y%m%d).zip
fi

$ANDROID_BUILD_TOP/build/tools/releasetools/img_from_target_files ${BUILD_TF} out/dist/img-$CURRENT_TIME.zip
mkdir $ANDROID_BUILD_TOP/out/dist/img
unzip $ANDROID_BUILD_TOP/out/dist/img-$CURRENT_TIME.zip -d $ANDROID_BUILD_TOP/out/dist/img
cp $ANDROID_BUILD_TOP/out/dist/img/*.img $OUT/
set +e
pack
set -e
#find the latest image in lichee/tools/pack
DEFAULT_IMG_UART0_FILE=$(ls -Art $ANDROID_BUILD_TOP/../lichee/tools/pack/ | grep "img" |tail -n 1)
cp $ANDROID_BUILD_TOP/../lichee/tools/pack/${DEFAULT_IMG_UART0_FILE} ${OUTPUT_DIR}/${TARGET_PRODUCT}_firmware_uart0.img

set +e
pack -d
set -e
#find the latest image in lichee/tools/pack
DEFAULT_IMG_CARD0_FILE=$(ls -Art $ANDROID_BUILD_TOP/../lichee/tools/pack/ | grep "img" |tail -n 1)
cp $ANDROID_BUILD_TOP/../lichee/tools/pack/${DEFAULT_IMG_CARD0_FILE} ${OUTPUT_DIR}/${TARGET_PRODUCT}_firmware_card0.img

#make ota full package

if [ ! "x${SIGN_DIR}" = "x" ]; then
    $ANDROID_BUILD_TOP/build/tools/releasetools/ota_from_target_files -k ${SIGN_DIR}/releasekey ${BUILD_TF} $ANDROID_BUILD_TOP/out/dist/${TARGET_PRODUCT}_ota_full.zip
else
    $ANDROID_BUILD_TOP/build/tools/releasetools/ota_from_target_files ${BUILD_TF} $ANDROID_BUILD_TOP/out/dist/${TARGET_PRODUCT}_ota_full.zip
fi


cp ${BUILD_TF} ${OUTPUT_DIR}/
cp $ANDROID_BUILD_TOP/out/dist/${TARGET_PRODUCT}_ota_full.zip ${OUTPUT_DIR}/

#make ota  incremental package
if [ ! "x${LAST_TF}" = "x" ]; then
    if [ ! "x${SIGN_DIR}" = "x" ]; then
        $ANDROID_BUILD_TOP/build/tools/releasetools/ota_from_target_files -k ${SIGN_DIR}/releasekey -i ${LAST_TF} ${BUILD_TF} $ANDROID_BUILD_TOP/out/dist/${TARGET_PRODUCT}_ota_inc.zip
    else
        $ANDROID_BUILD_TOP/build/tools/releasetools/ota_from_target_files -i ${LAST_TF} ${BUILD_TF} $ANDROID_BUILD_TOP/out/dist/${TARGET_PRODUCT}_ota_inc.zip
    fi
    cp $ANDROID_BUILD_TOP/out/dist/${TARGET_PRODUCT}_ota_inc.zip ${OUTPUT_DIR}/
fi

}

WORKDIR=$(pwd)
if [ ! -d ${WORKDIR}/.repo ]; then
    echo "is in android root dir?"
    exit 0
fi
if [ ! -e ${WORKDIR}/build/envsetup.sh ]; then
    echo "is in android root dir?"
    exit 1
fi

while getopts "i:o:p:s:" arg
do
    case $arg in
    i)
        LAST_TF=$OPTARG
        ;;
    o)
        OUTPUT_DIR=$OPTARG
        ;;
    p)
        PRODUCT=$OPTARG
        ;;
    s)
        SIGN_DIR=$OPTARG
        ;;
    ?)
        usage
        exit 1
        ;;
    esac
done

if [ ! "x${OUTPUT_DIR}" = "x" ]; then
    if [ -e ${OUTPUT_DIR} ]; then
        echo "output dir(${OUTPUT_DIR}) exist, remove it and try again..."
        exit 1
    fi
    mkdir -p ${OUTPUT_DIR}
    getAbsPath ${OUTPUT_DIR} OUTPUT_DIR
fi

if [ ! "x${SIGN_DIR}" = "x" ]; then
    getAbsPath ${SIGN_DIR} SIGN_DIR
    getAbsPath ${SIGN_DIR}/platform.x509.pem NULL
    getAbsPath ${SIGN_DIR}/releasekey.x509.pem NULL
    getAbsPath ${SIGN_DIR}/shared.x509.pem NULL
    getAbsPath ${SIGN_DIR}/media.x509.pem NULL
fi

if [ ! "x${LAST_TF}" = "x" ]; then
    getAbsPath ${LAST_TF} LAST_TF
fi

printBuiltPara

build

