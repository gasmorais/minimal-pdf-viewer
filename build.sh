#!/bin/zsh
set -euo pipefail
cd "${0:A:h}"

export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BUILD_TOOLS=$SDK/build-tools/36.0.0
PLATFORM=$SDK/platforms/android-36/android.jar
OUT=build
APK=minimal-pdf-viewer.apk
KEYSTORE=release.jks
PASSWORD_FILE=release.password

rm -rf $OUT
mkdir -p $OUT/generated $OUT/classes $OUT/dex

$BUILD_TOOLS/aapt2 compile --dir res -o $OUT/resources.zip
$BUILD_TOOLS/aapt2 link -o $OUT/base.apk -I $PLATFORM \
  --manifest AndroidManifest.xml --java $OUT/generated $OUT/resources.zip

$JAVA_HOME/bin/javac --release 17 -Xlint:all -Werror -classpath $PLATFORM -d $OUT/classes \
  $(find src $OUT/generated -name '*.java')
$BUILD_TOOLS/d8 --release --min-api 31 --lib $PLATFORM --output $OUT/dex \
  $(find $OUT/classes -name '*.class')

(cd $OUT/dex && zip -q ../base.apk classes.dex)
$BUILD_TOOLS/zipalign -f 4 $OUT/base.apk $OUT/aligned.apk

if [[ ! -f $KEYSTORE ]]; then
  openssl rand -hex 24 > $PASSWORD_FILE
  chmod 600 $PASSWORD_FILE
  PASSWORD=$(<$PASSWORD_FILE)
  $JAVA_HOME/bin/keytool -genkeypair -keystore $KEYSTORE -alias release -keyalg RSA -keysize 2048 \
    -validity 36500 -storepass $PASSWORD -keypass $PASSWORD -dname "CN=Minimal PDF Viewer"
fi
PASSWORD=$(<$PASSWORD_FILE)
$BUILD_TOOLS/apksigner sign --ks $KEYSTORE --ks-pass pass:$PASSWORD --out $APK $OUT/aligned.apk
echo "APK: $PWD/$APK"

if [[ "${1:-}" == "install" ]]; then
  adb install -r $APK
fi
