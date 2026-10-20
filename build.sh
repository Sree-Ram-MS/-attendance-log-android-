set -e
T=/workspace/work/sdk; BT=$T/android-14; AJ=$T/android-34/android.jar
export JAVA_HOME=$T/jdka/jdk-17.0.20.1+1; export PATH=$JAVA_HOME/bin:$PATH
rm -rf out && mkdir -p out/c out/dex out/gen
$T/st/build-tools/aapt2 compile --dir res -o out/res.zip
$T/st/build-tools/aapt2 link --java out/gen -A assets -o out/base.apk -I $AJ --manifest AndroidManifest.xml out/res.zip --min-sdk-version 24 --target-sdk-version 34
javac --release 8 -nowarn -classpath $AJ -d out/c $(find src out/gen -name "*.java") 2>&1 | grep -v warning || true
java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --min-api 21 --lib $AJ --output out/dex $(find out/c -name '*.class')
cp out/base.apk out/u.apk; (cd out/dex && zip -q ../u.apk classes.dex)
$T/st/build-tools/zipalign -f 4 out/u.apk out/a.apk
[ -f ../miniremote/key.jks ] && cp -n ../miniremote/key.jks . ; [ -f key.jks ] || keytool -genkeypair -keystore key.jks -alias r -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Sree Ram" >/dev/null 2>&1
java -jar $BT/lib/apksigner.jar sign --ks key.jks --ks-pass pass:android --out Attendance.apk out/a.apk
java -jar $BT/lib/apksigner.jar verify Attendance.apk && ls -la Attendance.apk
