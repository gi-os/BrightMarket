"""Write e2e-out/index.json: a test catalogue for the automatic-update emulator run."""
import datetime, hashlib, json, os, sys

out = sys.argv[1]
now = datetime.datetime.now(datetime.timezone.utc)
iso = lambda d: d.strftime("%Y-%m-%dT%H:%M:%SZ")
old, fresh = iso(now - datetime.timedelta(days=3)), iso(now - datetime.timedelta(hours=1))

def entry(pkg, name, apk, version, code, published, hold=False):
    data = open(os.path.join(out, apk), "rb").read()
    e = {"pkg": pkg, "name": name, "repo": "e2e/" + name, "category": "tools", "summary": "",
         "latest": {"version": version, "versionCode": code, "apk": "http://127.0.0.1:8000/" + apk,
                    "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                    "published": published, "notes": ""},
         "downloads": 0, "firstSeen": old}
    if hold:
        e["hold"] = True
    return e

apps = [
    entry("com.gios.e2e.owned", "Owned", "owned-v2.apk", "2.0", 2, old),
    entry("com.gios.e2e.foreign", "Foreign", "foreign-v2.apk", "2.0", 2, old),
    entry("com.gios.e2e.held", "Held", "held-v2.apk", "2.0", 2, old, hold=True),
    entry("com.gios.e2e.fresh", "Fresh", "fresh-v2.apk", "2.0", 2, fresh),
    entry("com.gios.e2e.oldtarget", "Oldtarget", "oldtarget-v2.apk", "2.0", 2, old),
    entry("com.gios.brightmarket", "BrightMarket", "bm-v2.apk", "1.33.0-e2e", 2, old),
]
json.dump({"format": 1, "apps": apps}, open(os.path.join(out, "index.json"), "w"), indent=1)
print("index.json:", len(apps), "apps")
