 plugins {
     id 'com.android.application'
     id 'org.jetbrains.kotlin.android'
 }

 android {
     namespace 'com.capri.app'
     compileSdk 34

     defaultConfig {
         applicationId 'com.capri.app'
         minSdk 24
         targetSdk 34
         versionCode 4
         versionName '1.3'
     }

     compileOptions {
         sourceCompatibility JavaVersion.VERSION_17
         targetCompatibility JavaVersion.VERSION_17
     }

     kotlinOptions {
         jvmTarget = '17'
     }
 }

 dependencies {
     implementation 'com.alphacephei:vosk-android:0.3.75@aar'
     implementation 'net.java.dev.jna:jna:5.18.1@aar'
 } 
