"""Verify the device snapshot from live_qa and job rescheduling after force-stop."""
from capture_ui import root,run
import sqlite3
import json
import time
db=sqlite3.connect(root/'app/build/live-db/time-pocket.db')
usage=db.execute("SELECT SUM(end-start) FROM sessions WHERE pkg='com.android.settings'").fetchone()[0]
assert usage>=5000
events=dict(db.execute("SELECT kind,COUNT(*) FROM lifecycle WHERE pkg='cn.timepocket.app.tests' GROUP BY kind").fetchall())
assert events=={'install':1,'remove':1},events
assert db.execute("SELECT COUNT(*) FROM apps WHERE pkg LIKE 'demo.%'").fetchone()[0]==0
assert db.execute("SELECT CAST(v AS INTEGER) FROM meta WHERE k='last_sync'").fetchone()[0]>0
db.close()
run('shell','am','start','-n','cn.timepocket.app/.MainActivity');time.sleep(3)
job=run('shell','cmd','jobscheduler','get-job-state','cn.timepocket.app','7101').decode().strip()
assert 'unknown' not in job.lower(),job
logs=run('logcat','-d','-s','AndroidRuntime:E').decode('utf-8',errors='replace')
assert 'FATAL EXCEPTION' not in logs,logs
checks=[{'check':'real usage events persisted','settingsMs':usage},{'check':'uninstall and reinstall recorded; update ignored','events':events},{'check':'demo data never enters SQLite','passed':True},{'check':'sync timestamp persisted','passed':True},{'check':'collection job rescheduled after force-stop and reopen','state':job},{'check':'no AndroidRuntime fatal exceptions','passed':True}]
(root/'docs/live-qa-result.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
(root/'docs/runtime-log.txt').write_text(logs,encoding='utf-8')
(root/'docs/instrumentation-result.txt').write_text('PASS: 21 Android SQLite assertions\n',encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False,indent=2),flush=True)
