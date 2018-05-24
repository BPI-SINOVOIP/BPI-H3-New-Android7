###############################################################################
# GMS home folder location
# Note: we cannot use $(my-dir) in this makefile
ANDROID_PARTNER_GMS_HOME := vendor/google


# GMS mandatory core packages
PRODUCT_PACKAGES := \
    ConfigUpdater \
    PrebuiltGmsCore \
    GoogleBackupTransport \
    GoogleExtServices \
    GoogleExtShared \
    GoogleFeedback \
    GoogleLoginService \
    GoogleOneTimeInitializer \
    GooglePartnerSetup \
    GooglePrintRecommendationService \
    GoogleServicesFramework \
    GoogleCalendarSyncAdapter \
    GoogleContactsSyncAdapter \
    GoogleTTS \
    Velvet \
    Phonesky \
    WebViewGoogle


# Overlay For GMS devices
$(call inherit-product, device/sample/products/backup_overlay.mk)
$(call inherit-product, device/sample/products/location_overlay.mk)
PRODUCT_PACKAGE_OVERLAYS += $(ANDROID_PARTNER_GMS_HOME)/products/gms_overlay


# Configuration files for GMS apps
PRODUCT_COPY_FILES += \
    $(ANDROID_PARTNER_GMS_HOME)/etc/preferred-apps/google.xml:system/etc/preferred-apps/google.xml \
    $(ANDROID_PARTNER_GMS_HOME)/etc/sysconfig/google.xml:system/etc/sysconfig/google.xml


# GMS mandatory application packages
PRODUCT_PACKAGES += \
    Chrome \
#    Drive \
#    Gmail2 \
#    Hangouts \
#    Maps \
#    Music2 \
#    Photos \
#    Velvet \
#    Videos \
#    YoutubePhone \
#    YoutubeTV

# GMS optional application packages
#PRODUCT_PACKAGES += \
#    Books \
#    CalendarGoogle \
#    CloudPrint \
#    DeskClockGoogle \
#    DMAgent \
#    FaceLock \
#    PlayGames \
#    PlusOne \
#    TagGoogle \
#    talkback \
#    AndroidPay

#PRODUCT_PACKAGES += \
#    EditorsDocs \
#    EditorsSheets \
#    EditorsSlides \
#    Keep \
#    Newsstand

# More GMS optional application packages
#PRODUCT_PACKAGES += \
#    CalculatorGoogle \
#    Messenger \
#    NewsWeather

# GMS sample application packages
#PRODUCT_PACKAGES += \
#    GmsSampleLayout

# Overrides
PRODUCT_PROPERTY_OVERRIDES += \
    ro.setupwizard.require_network=any \
    ro.setupwizard.mode=OPTIONAL \
    ro.com.google.gmsversion=7.0_r4 \
