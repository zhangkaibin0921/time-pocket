from pathlib import Path
import subprocess
import time
root=Path(__file__).resolve().parents[1]
adb=root/'.tools/android-sdk/platform-tools/adb.exe'
out=root/'docs/screenshots';out.mkdir(parents=True,exist_ok=True)
def run(*args,check=True):
    p=subprocess.run([str(adb),'-s','emulator-5554',*args],capture_output=True,check=check)
    print(p.stdout.decode('utf-8',errors='replace'),flush=True)
    if p.returncode:print(p.stderr.decode('utf-8',errors='replace'),flush=True)
    return p.stdout
deadline=time.time()+300
while time.time()<deadline:
    p=subprocess.run([str(adb),'-s','emulator-5554','shell','getprop','sys.boot_completed'],capture_output=True)
    if p.stdout.strip()==b'1':break
    time.sleep(3)
else:raise RuntimeError('Emulator did not boot within 300 seconds')
run('shell','input','keyevent','82')
run('install','-r',str(root/'dist/time-pocket-0.2.0-debug.apk'))
run('install','-r',str(root/'app/build/manual/instrumentation/tests.apk'))
result=run('shell','am','instrument','-w','cn.timepocket.app.tests/cn.timepocket.app.PocketInstrumentation')
(root/'docs/instrumentation-result.txt').write_bytes(result)
if b'FAIL:' in result or b'PASS:' not in result:raise RuntimeError('Instrumentation tests failed')
run('shell','am','start','-n','cn.timepocket.app/.MainActivity')
time.sleep(12)
with (out/'01-dashboard.png').open('wb') as f:subprocess.run([str(adb),'-s','emulator-5554','exec-out','screencap','-p'],stdout=f,check=True)
run('shell','uiautomator','dump','/sdcard/pocket-window.xml')
run('pull','/sdcard/pocket-window.xml',str(root/'app/build/window.xml'))
logs=run('logcat','-d','-s','AndroidRuntime:E')
(root/'docs/runtime-log.txt').write_bytes(logs)
