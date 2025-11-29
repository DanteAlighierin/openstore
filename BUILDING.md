## Building the app

### Getting started
Requirements/Dependencies: 
- tools: [listed here](https://codeberg.org/mi6e4ka/android-build-ct/src/branch/main/Dockerfile#L9-L26)
- SDK: `OpenJDK 17`
- [Android Studio](https://developer.android.com/studio) for app debugging

### Building debug release
Open Android Studio, open project and Sync Gradle. After, in toolbar go to Run and run the app.
Alternatively, run in terminal from project directory:
```
export ANDROID_HOME=/usr/lib/jvm/java-17-openjdk; # here the path to openjdk 17 \
export ANDROID_SDK_ROOT=$ANDROID_HOME; \
export JAVA_HOME=$ANDROID_HOME; \
./gradlew assembleDebug
```

### Building signed release
Create file named `keystore.properties` in project directory with next values:
```
storeFile=<full path to your keystore.jks>
keyAlias=<your key alias in keystore>
storePassword=<password for keystore>
keyPassword=<password for alias>
```

After that, run in terminal from project directory:
```
export ANDROID_HOME=/usr/lib/jvm/java-17-openjdk; # here the path to openjdk 17 \
export ANDROID_SDK_ROOT=$ANDROID_HOME; \
export JAVA_HOME=$ANDROID_HOME; \
./gradlew assembleRelease
```

Release artifacts will be located in `app/build/outputs/apk/release/` directory.
If you have no your keystore.jks, you can create it using `keytool` or just follow [this guide](https://developer.android.com/studio/publish/app-signing#generate-key) from Google for Android Studio.
