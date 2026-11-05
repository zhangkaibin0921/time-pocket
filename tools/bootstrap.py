"""Fetch official Android SDK components into this project only; validate archive hashes."""
from pathlib import Path
import hashlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import shutil

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
SDK = TOOLS / 'android-sdk'
BASE = 'https://dl.google.com/android/repository/'

def download(url, path, checksum=None, algorithm='sha1'):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        print('Downloading', url, flush=True)
        with urllib.request.urlopen(url, timeout=120) as r, path.with_suffix('.part').open('wb') as out:
            shutil.copyfileobj(r, out)
        path.with_suffix('.part').replace(path)
    if checksum:
        actual = hashlib.new(algorithm, path.read_bytes()).hexdigest()
        if actual.lower() != checksum.lower():
            raise RuntimeError('Checksum mismatch: ' + str(path))

def main():
    TOOLS.mkdir(exist_ok=True)
    xml = urllib.request.urlopen(BASE + 'repository2-1.xml', timeout=60).read()
    root = ET.fromstring(xml)
    for package, target in [('platforms;android-35', SDK/'platforms'/'android-35'),
                            ('build-tools;35.0.0', SDK/'build-tools'/'35.0.0'),
                            ('platform-tools', SDK/'platform-tools')]:
        if target.exists():
            print('Already installed:', target, flush=True)
            continue
        candidates = [p for p in root if p.tag.endswith('remotePackage') and p.attrib['path'] == package and p.find('channelRef').attrib['ref']=='channel-0']
        p = candidates[-1]
        archives=p.find('archives')
        a=next(a for a in archives if a.findtext('host-os') in (None,'windows'))
        complete=a.find('complete'); url=complete.findtext('url')
        checksum=complete.find('checksum')
        archive=TOOLS/url
        download(BASE+url,archive,checksum.text,checksum.attrib.get('type','sha1'))
        staging=TOOLS/('unpack-'+package.replace(';','-'))
        staging.mkdir(exist_ok=True)
        with zipfile.ZipFile(archive) as z:
            z.extractall(staging)
        children=[x for x in staging.iterdir() if x.name!='__MACOSX']
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.move(str(children[0]),str(target))
        print('Installed:',target,flush=True)
    (ROOT/'local.properties').write_text('sdk.dir='+str(SDK).replace('\\','/')+'\n',encoding='utf-8')

if __name__=='__main__': main()
