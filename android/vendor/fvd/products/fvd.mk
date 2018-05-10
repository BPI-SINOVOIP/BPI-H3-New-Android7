	
	
# miracast
#common System APK
PRODUCT_PACKAGES += \
	TvdFileManager \
	Camera2 \
	TvdVideo \
        TvLauncher \
        WebScreensaver \
	SettingsAssist \
	MiracastReceiver \
	AllCast \
	WebScreensaver

#install apk's lib to system/lib
PRODUCT_PACKAGES += \
	libjni_mosaic.so \
	libjni_WFDisplay.so \
	libwfdrtsp.so \
	libwfdplayer.so \
	libwfdmanager.so \
	libwfdutils.so \
	libwfduibc.so \
#   libjni_eglfence_awgallery.so \

#mango tv and FireLauncher
#PRODUCT_PACKAGES += \
#	libbspatch.so \
#	lib_All_imgoTV_bitmaps.so \
#	lib_All_imgoTV_nn_tv_air_control.so \
#	lib_All_imgoTV_nn_tv_client.so

#Homlet additional api
#isomount && securefile && gpioservice
PRODUCT_PACKAGES += \
    isomountmanagerservice \
    libisomountmanager_jni \
    libisomountmanagerservice \
    systemmixservice \
    libsystemmix_jni \
    libsystemmixservice \
    gpioservice \
    libgpio_jni \
    libgpioservice \
    libsecurefile_jni \
    libsecurefileservice \
    securefileserver \
    libadmanager_jni \
    libconfig_jni \
    libswconfig \
    libjni_swos

TARGET_BUSINESS_PLATFORM := fvd
