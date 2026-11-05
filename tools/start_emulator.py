from pathlib import Path
import os
import subprocess
root=Path(__file__).resolve().parents[1]
sdk=root/'.tools/android-sdk'
env=os.environ.copy();env['ANDROID_HOME']=str(sdk);env['ANDROID_SDK_ROOT']=str(sdk)
env['ANDROID_AVD_HOME']=str(root/'.tools/avd');env['ANDROID_USER_HOME']=str(root/'.tools/android-user')
Path(env['ANDROID_USER_HOME']).mkdir(exist_ok=True)
out=root/'app/build/emulator';out.mkdir(parents=True,exist_ok=True)
with (out/'stdout.log').open('w') as stdout,(out/'stderr.log').open('w') as stderr:
    p=subprocess.Popen([str(sdk/'emulator/emulator.exe'),'-avd','PocketQA','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader','-accel','on'],env=env,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Headless emulator PID',p.pid)
