#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk_path="${QIANXIA_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
if [[ -z "$sdk_path" ]]; then echo 'Set QIANXIA_SDK to your Android SDK directory' >&2; exit 1; fi
build_tools="$sdk_path/build-tools/35.0.0"
android_jar="$sdk_path/platforms/android-35/android.jar"
mkdir -p build/gen build/classes build/dex signing
"$build_tools/aapt" package -f -m -J build/gen -M app/src/main/AndroidManifest.xml -S app/src/main/res -I "$android_jar" -F build/resources.apk
classpath="$android_jar:libs/kotlin-stdlib-2.1.21.jar:libs/okio-jvm-3.18.1.jar:libs/annotations-23.0.0.jar:libs-android/okhttp-android-5.5.0.jar:libs-android/startup-runtime-1.2.0.jar"
mapfile -t sources < <(find app/src/main/java build/gen -name '*.java' | sort)
if command -v javac >/dev/null; then
 javac -encoding UTF-8 --release 8 -cp "$classpath" -d build/classes "${sources[@]}"
else
 java -jar "${QIANXIA_ECJ:-tooling/ecj.jar}" -8 -proc:none -encoding UTF-8 -cp "$classpath" -d build/classes "${sources[@]}"
fi
python3 - <<'PY'
import zipfile
from pathlib import Path
with zipfile.ZipFile('build/app-classes.jar','w') as z:
 for f in Path('build/classes').rglob('*.class'):z.write(f,f.relative_to('build/classes'))
PY
"$build_tools/d8" --min-api 28 --lib "$android_jar" --output build/dex build/app-classes.jar libs/kotlin-stdlib-2.1.21.jar libs/okio-jvm-3.18.1.jar libs/annotations-23.0.0.jar libs-android/okhttp-android-5.5.0.jar libs-android/startup-runtime-1.2.0.jar
cp build/resources.apk build/unsigned.apk
python3 - <<'PY'
import zipfile
from pathlib import Path
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in Path('build/dex').glob('*.dex'):z.write(f,f.name)
 for f in Path('app/src/main/assets').rglob('*'):
  if f.is_file():z.write(f,'assets/'+str(f.relative_to('app/src/main/assets')))
 for jar in [Path('libs-android/okhttp-android-5.5.0.jar')]:
  with zipfile.ZipFile(jar) as library:
   for name in library.namelist():
    if name.endswith('.gz') or name.endswith('.list'):z.writestr(name,library.read(name))
PY
"$build_tools/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ ! -f signing/development.jks ]]; then keytool -genkeypair -keystore signing/development.jks -storepass qianxia-development -keypass qianxia-development -alias qianxia -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Qianxia Development' >/dev/null; fi
"$build_tools/apksigner" sign --ks signing/development.jks --ks-key-alias qianxia --ks-pass pass:qianxia-development --key-pass pass:qianxia-development --out build/Qianxia-1.0.0.apk build/aligned.apk
"$build_tools/apksigner" verify --verbose build/Qianxia-1.0.0.apk
