"""Dependency-free APK build using the official SDK tools (JDK 17 required)."""
from pathlib import Path
import os
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('ANDROID_HOME',str(ROOT/'.tools'/'android-sdk')))
BT=SDK/'build-tools'/'35.0.0'
JAR=SDK/'platforms'/'android-35'/'android.jar'
OUT=ROOT/'app'/'build'/'manual'
JAVA=Path(os.environ['JAVA_HOME'])/'bin'

def run(args):
    args=[str(x) for x in args]
    print('>',Path(args[0]).name,' '.join(args[1:3]),flush=True)
    subprocess.run(args,check=True,cwd=ROOT)

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for name in ['gen','classes','dex']:(OUT/name).mkdir(exist_ok=True)
    # Run deterministic JVM regression tests before packaging.
    testout=OUT/'tests';testout.mkdir(exist_ok=True)
    engine=ROOT/'app/src/main/java/cn/timepocket/app/UsageEngine.java'
    run([JAVA/'javac.exe','-encoding','UTF-8','-d',testout,engine,ROOT/'tests/UsageEngineTest.java'])
    run([JAVA/'java.exe','-ea','-cp',testout,'cn.timepocket.app.UsageEngineTest'])
    run([JAVA/'javac.exe','-encoding','UTF-8','-d',testout,ROOT/'app/src/main/java/cn/timepocket/app/DayTimeline.java',ROOT/'tests/DayTimelineTest.java'])
    run([JAVA/'java.exe','-ea','-cp',testout,'cn.timepocket.app.DayTimelineTest'])
    run([JAVA/'javac.exe','-encoding','UTF-8','-d',testout,ROOT/'app/src/main/java/cn/timepocket/app/UsageBuckets.java',ROOT/'tests/UsageBucketsTest.java'])
    run([JAVA/'java.exe','-ea','-cp',testout,'cn.timepocket.app.UsageBucketsTest'])
    run([BT/'aapt2.exe','compile','--dir',ROOT/'app/src/main/res','-o',OUT/'resources.zip'])
    manifest=OUT/'AndroidManifest.xml'
    manifest.write_text((ROOT/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8').replace('<manifest ', '<manifest package="cn.timepocket.app" ',1),encoding='utf-8')
    run([BT/'aapt2.exe','link','-o',OUT/'base.apk','-I',JAR,'--manifest',manifest,'--java',OUT/'gen','--min-sdk-version','26','--target-sdk-version','35','--version-code','38','--version-name','0.6.8','--debug-mode',OUT/'resources.zip'])
    files=list((ROOT/'app/src/main/java').rglob('*.java'))+list((OUT/'gen').rglob('*.java'))
    argfile=OUT/'sources.txt'
    argfile.write_text('\n'.join('"'+str(f).replace('\\','/')+'"' for f in files),encoding='utf-8')
    run([JAVA/'javac.exe','-encoding','UTF-8','--release','17','-classpath',JAR,'-d',OUT/'classes','@'+str(argfile)])
    run([JAVA/'jar.exe','--create','--file',OUT/'classes.jar','-C',OUT/'classes','.'])
    run([JAVA/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',JAR,'--min-api','26','--output',OUT/'dex',OUT/'classes.jar'])
    shutil.copyfile(OUT/'base.apk',OUT/'unsigned.apk')
    with zipfile.ZipFile(OUT/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
        for f in (OUT/'dex').glob('*.dex'):z.write(f,f.name)
    run([BT/'zipalign.exe','-f','-p','4',OUT/'unsigned.apk',OUT/'aligned.apk'])
    key=ROOT/'.tools/debug.keystore'
    key.parent.mkdir(parents=True,exist_ok=True)
    if not key.exists():
        run([JAVA/'keytool.exe','-genkeypair','-keystore',key,'-storepass','android','-alias','androiddebugkey','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Time Pocket Debug,O=Local Development,C=CN'])
    dest=ROOT/'dist';dest.mkdir(exist_ok=True)
    apk=dest/'time-pocket-0.6.8-debug.apk'
    run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,OUT/'aligned.apk'])
    run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','verify','--verbose',apk])
    run([BT/'aapt2.exe','dump','badging',apk])
    print('APK:',apk,flush=True)

if __name__=='__main__':main()
