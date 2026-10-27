import json
import os
import sys
import time
import urllib.request

def main():
    ver_code = os.environ.get("VER_CODE")
    ver_name = os.environ.get("VER_NAME", "")
    commit_msg = os.environ.get("COMMIT_MSG", "")
    download_url = os.environ.get("DOWNLOAD_URL", "")

    if not ver_code:
        print("VER_CODE is required")
        sys.exit(1)

    payload = {
        "versionCode": int(ver_code),
        "versionName": f"v{ver_name.lstrip('v')}",
        "releaseNotes": commit_msg,
        "downloadUrl": download_url,
        "timestamp": int(time.time() * 1000)
    }

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        "https://ashuchat-51b3e-default-rtdb.asia-southeast1.firebasedatabase.app/app_update.json",
        data=data,
        headers={"Content-Type": "application/json"},
        method="PUT"
    )

    try:
        with urllib.request.urlopen(req) as resp:
            print(f"Firebase updated successfully: {resp.status}")
    except Exception as e:
        print(f"Firebase sync warning: {e}")

if __name__ == "__main__":
    main()
