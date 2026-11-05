"""Run Android-independent Java regression tests with JDK 17 on any desktop OS."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SUITES = ("UsageEngine", "DayTimeline", "UsageBuckets")


def java_tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
        raise SystemExit(f"{executable} not found in JAVA_HOME; select JDK 17.")
    candidate = shutil.which(executable)
    if not candidate:
        raise SystemExit(f"{executable} not found; install JDK 17 and set JAVA_HOME.")
    return candidate


def main():
    javac, java = java_tool("javac"), java_tool("java")
    source = ROOT / "app/src/main/java/cn/timepocket/app"
    with tempfile.TemporaryDirectory(prefix="time-pocket-tests-") as output:
        files = [path for suite in SUITES for path in
                 (source / f"{suite}.java", ROOT / "tests" / f"{suite}Test.java")]
        subprocess.run([javac, "-encoding", "UTF-8", "--release", "17",
                        "-d", output, *map(str, files)], check=True)
        for suite in SUITES:
            subprocess.run([java, "-ea", "-cp", output,
                            f"cn.timepocket.app.{suite}Test"], check=True)


if __name__ == "__main__":
    main()
