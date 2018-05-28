# BPI-H3-New-Android7
BPI-M2-Plus-Zero Android7 (Agents Version)
----------
1 Build Android BSP

 $ cd lichee
 
 $ ./build.sh config       

Welcome to mkscript setup progress
All available chips:
   1. sun8iw7p1
   
Choice: 1


All available platforms:
   1. android
   2. dragonboard
   3. linux
   4. camdroid

Choice: 1


All available kernel:
   1. linux-4.4
 
Choice: 1

   $ ./build.sh 

***********

2 Build Android 

   $cd ../android

   $source build/envsetup.sh
   
   $lunch    //(dolphin_bpi_m2p-eng  or dolphin_bpi_m2z-eng)
   
   $extract-bsp
   
   $make -j8
   
   $pack
