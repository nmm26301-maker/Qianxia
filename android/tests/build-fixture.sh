#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${QIANXIA_SDK:?}"
bt="$sdk/build-tools/35.0.0"; aj="$sdk/platforms/android-35/android.jar"
mkdir -p build/fixture/gen build/fixture/classes build/fixture/dex
"$bt/aapt" package -f -m -J build/fixture/gen -M tests/fixture/AndroidManifest.xml -S tests/fixture/res -I "$aj" -F build/fixture/resources.apk
java -jar tooling/ecj.jar -8 -proc:none -cp "$aj" -d build/fixture/classes tests/fixture/src/com/qianxia/fixture/MainActivity.java
python3 - <<'PY'
from pathlib import Path
import zipfile
with zipfile.ZipFile('build/fixture/classes.jar','w')as z:
 for f in Path('build/fixture/classes').rglob('*.class'):z.write(f,f.relative_to('build/fixture/classes'))
PY
"$bt/d8" --min-api 28 --lib "$aj" --output build/fixture/dex build/fixture/classes.jar
cp build/fixture/resources.apk build/fixture/unsigned.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/fixture/unsigned.apk','a',zipfile.ZIP_DEFLATED)as z:z.write('build/fixture/dex/classes.dex','classes.dex')
PY
"$bt/zipalign" -f 4 build/fixture/unsigned.apk build/fixture/aligned.apk
"$bt/apksigner" sign --ks signing/development.jks --ks-pass pass:qianxia-development --ks-key-alias qianxia --out build/fixture/fixture.apk build/fixture/aligned.apk
