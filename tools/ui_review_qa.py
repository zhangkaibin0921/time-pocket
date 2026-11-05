"""UI review on the dedicated PocketQA emulator; captures and restores display overrides."""
import sys
import time
from capture_ui import run, tap, nodes, root

sys.stdout.reconfigure(encoding='utf-8')
version=sys.argv[1] if len(sys.argv)>1 else '0.5.2'
tag=version.replace('.','')
out=root/f'docs/screenshots-v{tag}'
out.mkdir(exist_ok=True)
results=[]
def passed(message):
    results.append('PASS: '+message)
    print(results[-1],flush=True)
def capture(name):
    (out/name).write_bytes(run('exec-out','screencap','-p'))
def exists(text):
    return any(n.get('text')==text or n.get('content-desc')==text for n in nodes().iter('node'))
def launch():
    run('shell','am','force-stop','cn.timepocket.app')
    run('shell','am','start','-W','-n','cn.timepocket.app/.MainActivity')
    time.sleep(2)
def seek(label):
    for _ in range(15):
        if exists(label):return
        run('shell','input','swipe','540','1750','540','650','350');time.sleep(.4)
    raise AssertionError('Not found: '+label)

try:
    run('shell','wm','size','reset');run('shell','settings','put','system','font_scale','1.0')
    print(run('install','-r',str(root/f'dist/time-pocket-{version}-debug.apk')).decode(),flush=True)
    run('logcat','-c');launch()
    for tab,name in [('时光','home'),('一天','day'),('足迹','footprints'),('口袋','pocket')]:
        tap(tab);capture('after-'+name+'.png')
        assert any(n.get('content-desc')==tab and n.get('selected')=='true' for n in nodes().iter('node'))
        passed(tab+' navigation selection')
    tap('足迹');assert not exists('待核实')
    tap('安装');assert exists('筛选应用 ▾')
    tap('卸载');assert exists('筛选应用 ▾')
    passed('footprint filter')
    tap('一天');run('shell','input','swipe','540','1750','540','850','400');time.sleep(.5);capture('after-timeline.png')
    tap('时光');tap('自选');tap('1');tap('2');capture('after-calendar.png')
    descriptions=[n.get('content-desc','') for n in nodes().iter('node')]
    start=next(d.split('，')[0] for d in descriptions if '，开始日期' in d)
    end=next(d.split('，')[0] for d in descriptions if '，结束日期' in d)
    tap('查看统计');assert exists(start+' — '+end+'  ·  包含起止日期')
    passed('single calendar range applied')
    launch();tap('一天');tap('前一天');tap('＋ 记一件事');capture('after-editor.png')
    tap('活动名称');run('shell','input','text','UICheck040');capture('after-keyboard.png')
    assert exists('保存活动');run('shell','input','keyevent','4');tap('分类：学习');tap('保存活动')
    seek('UICheck040');tap('UICheck040');assert exists('手记详情');tap('编辑手记');assert exists('分类：学习');tap('关闭编辑，取消修改')
    launch();tap('一天');tap('前一天');seek('UICheck040');tap('UICheck040');tap('编辑手记')
    tap('删除这条手记');tap('删除手记');assert not exists('UICheck040')
    passed('manual entry save, persistence, edit and delete')
    run('shell','wm','size','840x1680');run('shell','settings','put','system','font_scale','1.3');launch()
    capture('small-home.png');tap('更多操作');tap('页面说明');assert exists('统计说明');tap('知道了')
    tap('一天');capture('small-day.png');tap('口袋');capture('small-pocket.png');tap('时光');tap('自选');capture('small-calendar.png');tap('取消')
    launch();tap('一天');tap('前一天');tap('＋ 记一件事');capture('small-editor.png');assert exists('保存活动')
    tap('关闭编辑，取消修改');passed('320dp screen, 1.3 font scale and reachable editor controls')
    logs=run('logcat','-d','-s','AndroidRuntime:E').decode('utf-8',errors='replace')
    assert 'FATAL EXCEPTION' not in logs,logs
    passed('no AndroidRuntime crash during walkthrough')
finally:
    run('shell','wm','size','reset');run('shell','settings','put','system','font_scale','1.0')
    (root/f'docs/ui-review-v{tag}.txt').write_text('\n'.join(results)+'\n',encoding='utf-8')
