#!/usr/bin/env python3
"""Check the actual APK against the identity of the installed built-in app."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def checked_output(args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--identity", type=Path, default=Path("config/release-identity.json"))
    args = parser.parse_args()
    identity = json.loads(args.identity.read_text())
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise SystemExit("ANDROID_HOME is required")
    tools = Path(sdk) / "build-tools/34.0.0"
    signature = checked_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(args.apk)])
    digests = re.findall(r"certificate SHA-256 digest:\s*([a-fA-F0-9:]+)", signature)
    actual = {d.replace(":", "").lower() for d in digests}
    if actual != {identity["certificateSha256"].lower()}:
        raise SystemExit("APK signing certificate does not match the original installed release")
    badging = checked_output([str(tools / "aapt"), "dump", "badging", str(args.apk)])
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    if package is None or package.group(1) != identity["applicationId"]:
        raise SystemExit("APK application ID does not match the installed release")
    if int(package.group(2)) <= identity["minimumVersionCode"]:
        raise SystemExit("APK version code must be newer than the original release")
    if "application-debuggable" in badging:
        raise SystemExit("Expected a non-debuggable release APK")
    label = re.search(r"^application-label:'([^']*)'", badging, re.M)
    if label is None or label.group(1) != "奶龙刷题":
        raise SystemExit("The app launcher name must be 奶龙刷题")
    if "android.permission.INTERNET" in badging:
        raise SystemExit("Offline PDF import must not request Internet access")
    checked_output([str(tools / "zipalign"), "-c", "-p", "4", str(args.apk)])
    banks = {}
    with zipfile.ZipFile(args.apk) as archive:
        if archive.testzip() is not None:
            raise SystemExit("APK ZIP integrity check failed")
        for name in ["questions.json", "questions_dangzhang.json", "questions_dangshi.json"]:
            bank = json.loads(archive.read("assets/" + name))
            if bank["count"] != len(bank["questions"]):
                raise SystemExit("Bundled question count mismatch: " + name)
            banks[name] = bank["count"]
    info = {
        "applicationId": package.group(1),
        "versionCode": int(package.group(2)),
        "versionName": package.group(3),
        "certificateSha256": identity["certificateSha256"],
        "apkSha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "banks": banks,
    }
    args.apk.with_name("release-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(info, ensure_ascii=False))


if __name__ == "__main__":
    main()
