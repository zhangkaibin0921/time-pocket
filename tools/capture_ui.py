from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
adb=str(root/'.tools/android-sdk/platform-tools/adb.exe')
out=root/'docs/screenshots';out.mkdir(exist_ok=True)
def run(*args):return subprocess.check_output([adb,'-s','emulator-5554',*args],stderr=subprocess.STDOUT)
def nodes():
    run('shell','uiautomator','dump','/sdcard/pocket-window.xml')
    return ET.fromstring(run('shell','cat','/sdcard/pocket-window.xml'))
def tap(label):
    tree=nodes()
    node=next(n for n in tree.iter('node') if n.get('text','').strip()==label or n.get('content-desc')==label)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1)
def capture(name):
    (out/name).write_bytes(run('exec-out','screencap','-p'))
    print('Captured',name,flush=True)
if __name__=='__main__':
    run('shell','am','force-stop','cn.timepocket.app')
    run('shell','am','start','-n','cn.timepocket.app/.MainActivity');time.sleep(5)
    capture('01-dashboard.png')
    tap('近7天');capture('02-week.png')
    run('shell','input','swipe','540','1800','540','850','500');time.sleep(1);capture('03-ranking.png')
    tap('足迹');capture('04-footprints.png')
    tap('口袋');capture('05-settings.png')
    run('shell','am','force-stop','cn.timepocket.app')
    run('shell','am','start','-n','cn.timepocket.app/.MainActivity');time.sleep(5)
    capture('06-onboarding.png')
