"""Regression: system broadcasts while Activity is paused, plus real usage and v1 upgrade."""
from capture_ui import root,run,tap
import subprocess,time,sqlite3,json
adb=str(root/'.tools/android-sdk/platform-tools/adb.exe')
deadline=time.time()+240
while time.time()<deadline:
    p=subprocess.run([adb,'-s','emulator-5554','shell','getprop','sys.boot_completed'],capture_output=True,timeout=15)
    if p.stdout.strip()==b'1':break
    time.sleep(2)
else:raise RuntimeError('Emulator boot timeout')
run('shell','input','keyevent','82')
print(run('install','-r',str(root/'dist/time-pocket-0.2.0-debug.apk')).decode(),flush=True)
print(run('install','-r',str(root/'app/build/manual/instrumentation/tests.apk')).decode(),flush=True)
result=run('shell','am','instrument','-w','cn.timepocket.app.tests/cn.timepocket.app.PocketInstrumentation')
(root/'docs/v02-instrumentation.txt').write_bytes(result);print(result.decode(),flush=True)
assert b'PASS:' in result and b'FAIL:' not in result
run('shell','logcat','-c')
run('shell','appops','set','cn.timepocket.app','GET_USAGE_STATS','allow')
run('shell','am','start','-W','-n','cn.timepocket.app/.MainActivity');time.sleep(4)
def snapshot(name):
    run('shell','am','force-stop','cn.timepocket.app')
    dest=root/'app/build'/name;dest.mkdir(exist_ok=True)
    for filename in ['time-pocket.db','time-pocket.db-wal','time-pocket.db-shm']:
        try:(dest/filename).write_bytes(run('exec-out','run-as','cn.timepocket.app','cat','databases/'+filename))
        except subprocess.CalledProcessError:pass
    return sqlite3.connect(dest/'time-pocket.db')
with snapshot('v02-before') as db:
    before=dict(db.execute("SELECT kind,COUNT(*) FROM lifecycle WHERE pkg='cn.timepocket.app.tests' AND source='broadcast' GROUP BY kind"))
run('shell','am','start','-W','-n','cn.timepocket.app/.MainActivity');time.sleep(2)
run('shell','am','start','-W','-a','android.settings.SETTINGS');time.sleep(3)
print('Installing/uninstalling while Time Pocket Activity is paused',flush=True)
run('uninstall','cn.timepocket.app.tests');time.sleep(2)
run('install',str(root/'app/build/manual/instrumentation/tests.apk'));time.sleep(2)
run('install','-r',str(root/'app/build/manual/instrumentation/tests.apk'));time.sleep(2)
with snapshot('v02-background') as db:
    after=dict(db.execute("SELECT kind,COUNT(*) FROM lifecycle WHERE pkg='cn.timepocket.app.tests' AND source='broadcast' GROUP BY kind"))
    delta={k:after.get(k,0)-before.get(k,0) for k in ['install','remove']}
    assert delta=={'install':1,'remove':1},delta
    schema=db.execute('PRAGMA user_version').fetchone()[0];assert schema==2
print('PASS: background Activity receiver + update exclusion',flush=True)
run('shell','am','start','-W','-n','cn.timepocket.app/.MainActivity');time.sleep(7)
tap('刷新本地记录');time.sleep(3)
tap('足迹')
shots=root/'docs/screenshots-v02';shots.mkdir(exist_ok=True)
(shots/'footprints.png').write_bytes(run('exec-out','screencap','-p'))
with snapshot('v02-usage') as db:
    usage=db.execute("SELECT SUM(end-start) FROM sessions WHERE pkg='com.android.settings'").fetchone()[0]
    assert usage and usage>5000,usage
    raw=db.execute('SELECT COUNT(*) FROM raw_events').fetchone()[0];assert raw>0
    legacy=db.execute("SELECT COUNT(*) FROM lifecycle WHERE source='legacy'").fetchone()[0]
logs=run('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace');assert 'FATAL EXCEPTION' not in logs,logs
report={'backgroundBroadcastDelta':delta,'schema':schema,'settingsUsageMs':usage,'rawEvents':raw,'legacyFootprintsPreserved':legacy,'fatalExceptions':False}
(root/'docs/v02-live-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2),flush=True)
