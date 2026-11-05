"""Build the small platform instrumentation APK; run tools/build.py first."""
from build import ROOT, OUT, JAVA, BT, JAR, run
import zipfile
import shutil

def main():
    test=OUT/'instrumentation';test.mkdir(exist_ok=True)
    (test/'classes').mkdir(exist_ok=True);(test/'dex').mkdir(exist_ok=True)
    manifest=test/'AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.timepocket.app.tests">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
    <application android:label="Pocket QA" android:debuggable="true" />
    <instrumentation android:name="cn.timepocket.app.PocketInstrumentation" android:targetPackage="cn.timepocket.app" android:label="Pocket SQLite checks" />
</manifest>''')
    run([BT/'aapt2.exe','link','-o',test/'base.apk','-I',JAR,'--manifest',manifest])
    run([JAVA/'javac.exe','-encoding','UTF-8','--release','17','-classpath',str(JAR)+';'+str(OUT/'classes'),'-d',test/'classes',ROOT/'app/src/androidTest/java/cn/timepocket/app/PocketInstrumentation.java'])
    run([JAVA/'jar.exe','--create','--file',test/'classes.jar','-C',test/'classes','.'])
    run([JAVA/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',JAR,'--classpath',OUT/'classes.jar','--min-api','26','--output',test/'dex',test/'classes.jar'])
    shutil.copyfile(test/'base.apk',test/'unsigned.apk')
    with zipfile.ZipFile(test/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
        for f in (test/'dex').glob('*.dex'):z.write(f,f.name)
    run([BT/'zipalign.exe','-f','-p','4',test/'unsigned.apk',test/'aligned.apk'])
    run([JAVA/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',ROOT/'.tools/debug.keystore','--ks-pass','pass:android','--out',test/'tests.apk',test/'aligned.apk'])

if __name__=='__main__':main()
