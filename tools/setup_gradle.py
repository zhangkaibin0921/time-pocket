from pathlib import Path
import urllib.request

root=Path(__file__).resolve().parents[1]
base='https://raw.githubusercontent.com/gradle/gradle/v8.11.1/'
for name in ['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar']:
    path=root/name
    path.parent.mkdir(parents=True,exist_ok=True)
    with urllib.request.urlopen(base+name,timeout=60) as response:
        path.write_bytes(response.read())
checksum=urllib.request.urlopen('https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256',timeout=60).read().decode().strip()
(root/'gradle/wrapper/gradle-wrapper.properties').write_text('distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip\ndistributionSha256Sum='+checksum+'\nnetworkTimeout=60000\nvalidateDistributionUrl=true\nzipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n',encoding='utf-8')
print('Gradle 8.11.1 wrapper ready')
