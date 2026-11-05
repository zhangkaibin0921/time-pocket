"""Exercise real usage events and install/remove/update broadcasts on our emulator."""
from capture_ui import root, run, capture
import sqlite3
import time
import json
checks=[]
run('shell','appops','set','cn.timepocket.app','GET_USAGE_STATS','allow')
run('shell','am','force-stop','cn.timepocket.app')
run('shell','am','start','-n','cn.timepocket.app/.MainActivity');time.sleep(6)
run('shell','am','start','-a','android.settings.SETTINGS');time.sleep(10)
run('shell','am','start','-n','cn.timepocket.app/.MainActivity');time.sleep(6)
run('shell','input','keyevent','3');time.sleep(1)
run('shell','am','start','-n','cn.timepocket.app/.MainActivity');time.sleep(4)
# The test APK is safe to remove; do not touch any user app.
run('uninstall','cn.timepocket.app.tests');time.sleep(3)
run('install',str(root/'app/build/manual/instrumentation/tests.apk'));time.sleep(3)
run('install','-r',str(root/'app/build/manual/instrumentation/tests.apk'));time.sleep(3)
capture('07-real-records.png')
run('shell','am','force-stop','cn.timepocket.app')
copy=root/'app/build/live-db';copy.mkdir(exist_ok=True)
for name in ['time-pocket.db','time-pocket.db-wal','time-pocket.db-shm']:
    try:(copy/name).write_bytes(run('exec-out','run-as','cn.timepocket.app','cat','databases/'+name))
    except Exception:pass
db=sqlite3.connect(copy/'time-pocket.db')
usage=db.execute("SELECT SUM(end-start) FROM sessions WHERE pkg='com.android.settings'").fetchone()[0]
assert usage and usage>=5000,('real settings foreground duration',usage)
checks.append({'check':'real UsageStats events are persisted','settingsMs':usage})
foot=db.execute("SELECT kind,COUNT(*) FROM lifecycle WHERE pkg='cn.timepocket.app.tests' GROUP BY kind").fetchall()
assert dict(foot)=={'install':1,'remove':1},foot
checks.append({'check':'uninstall + reinstall recorded; update ignored','events':dict(foot)})
assert db.execute("SELECT COUNT(*) FROM apps WHERE pkg LIKE 'demo.%'").fetchone()[0]==0
checks.append({'check':'demo mode never pollutes SQLite','passed':True})
assert db.execute("SELECT CAST(v AS INTEGER) FROM meta WHERE k='last_sync'").fetchone()[0]>0
checks.append({'check':'sync timestamp persisted','passed':True})
db.close()
# Force-stop intentionally cancels jobs. Relaunch reschedules the persistent job.
run('shell','am','start','-n','cn.timepocket.app/.MainActivity');time.sleep(3)
job=run('shell','cmd','jobscheduler','get-job-state','cn.timepocket.app','7101').decode().strip()
assert 'unknown' not in job.lower(),job
checks.append({'check':'persistent collection job scheduled','state':job})
logs=run('logcat','-d','-s','AndroidRuntime:E').decode('utf-8',errors='replace')
assert 'FATAL EXCEPTION' not in logs,logs
checks.append({'check':'no AndroidRuntime fatal exceptions','passed':True})
(root/'docs/live-qa-result.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
(root/'docs/runtime-log.txt').write_text(logs,encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False,indent=2),flush=True)
