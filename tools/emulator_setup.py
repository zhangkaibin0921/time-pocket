"""Install an isolated official Android emulator for local QA."""
from bootstrap import ROOT, TOOLS, SDK, BASE, download
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import shutil

def install(repository, package, dest):
    if dest.exists(): return
    data=urllib.request.urlopen(repository, timeout=90).read()
    root=ET.fromstring(data)
    candidates=[p for p in root if p.tag.endswith('remotePackage') and p.attrib['path']==package]
    candidates=[p for p in candidates if p.find('channelRef') is None or p.find('channelRef').attrib['ref']=='channel-0']
    p=candidates[-1]
    a=next(a for a in p.find('archives') if a.findtext('host-os') in (None,'windows'))
    complete=a.find('complete');url=complete.findtext('url');checksum=complete.find('checksum')
    archive=TOOLS/url.split('/')[-1]
    full=url if url.startswith('https:') else repository.rsplit('/',1)[0]+'/'+url
    print('Archive bytes:',complete.findtext('size'),flush=True)
    download(full,archive,checksum.text,checksum.attrib.get('type','sha1'))
    staging=TOOLS/('qa-unpack-'+package.replace(';','-'));staging.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:z.extractall(staging)
    child=next(x for x in staging.iterdir() if x.is_dir() and x.name!='__MACOSX')
    dest.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(child),str(dest))
    print('Installed',package,flush=True)

if __name__=='__main__':
    install(BASE+'repository2-1.xml','emulator',SDK/'emulator')
    install(BASE+'sys-img/android/sys-img2-1.xml','system-images;android-35;default;x86_64',SDK/'system-images/android-35/default/x86_64')
    avd=TOOLS/'avd'/'PocketQA.avd';avd.mkdir(parents=True,exist_ok=True)
    (TOOLS/'avd'/'PocketQA.ini').write_text('avd.ini.encoding=UTF-8\npath='+str(avd)+'\ntarget=android-35\n')
    (avd/'config.ini').write_text('''AvdId=PocketQA
avd.ini.displayname=Pocket QA
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.mainKeys=no
disk.dataPartition.size=2G
image.sysdir.1=system-images/android-35/default/x86_64/
tag.id=default
tag.display=Default
showDeviceFrame=no
''',encoding='utf-8')
