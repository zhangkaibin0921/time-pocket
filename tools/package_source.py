"""Package source and docs without Git metadata, signing keys or local data."""
from pathlib import Path
import hashlib
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIRECTORIES = ("app/src", "gradle", "tests", "tools", "docs", ".github")
FILES = ("README.md", "CHANGELOG.md", "CONTRIBUTING.md", "THIRD_PARTY_NOTICES.md",
         "LICENSE", "LICENSE.md", ".gitignore", ".gitattributes", "build.gradle",
         "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "app/build.gradle")
SKIP = {".git", ".tools", ".gradle", ".build-cache", "build", "dist", "__pycache__", ".idea", ".venv"}
PRIVATE_SUFFIXES = {".jks", ".keystore", ".pem", ".p12", ".pfx", ".pyc", ".log",
                    ".db", ".db-wal", ".db-shm", ".sqlite", ".sqlite3", ".apk", ".idsig"}


def main():
    config = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    version = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", config).group(1)
    dest = ROOT / "dist"
    apk = dest / f"time-pocket-{version}-debug.apk"
    if not apk.is_file():
        raise SystemExit("Build the APK first with: python tools/build.py")
    candidates = [ROOT / name for name in FILES]
    for directory in DIRECTORIES:
        candidates.extend((ROOT / directory).rglob("*"))
    files = sorted({p for p in candidates if p.is_file() and not p.is_symlink()
                    and not SKIP.intersection(p.relative_to(ROOT).parts)
                    and p.suffix.lower() not in PRIVATE_SUFFIXES
                    and p.name not in {"local.properties", "key.properties", "keystore.properties"}
                    and not p.name.startswith(".env")})
    archive = dest / f"time-pocket-{version}-source.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in files:
            bundle.write(path, Path("time-pocket") / path.relative_to(ROOT))
    (dest / "SHA256SUMS.txt").write_text(
        "\n".join(hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name
                  for p in (apk, archive)) + "\n", encoding="utf-8")
    print("Source files:", len(files))
    for path in (apk, archive):
        print(path.name, path.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
