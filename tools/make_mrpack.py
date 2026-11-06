#!/usr/bin/env python3
"""
Собирает модпак MobileCraft.mrpack (формат Modrinth) для встраивания в лаунчер.

  python3 tools/make_mrpack.py <mobilecraft.jar> <out_dir>

На выходе:
  out_dir/pack.mrpack — модпак: Fabric 26.1.2, моды с Modrinth (по ссылкам с хешами),
                        overrides/mods/mobilecraft.jar, overrides/servers.dat
  out_dir/pack.json   — адрес сервера и ревизия сборки (для экрана «Играть»)
"""
import hashlib
import io
import json
import os
import struct
import sys
import urllib.parse
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {"User-Agent": "car-toha/launcher MobileCraft-build"}


def get_json(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def modrinth_file(slug, mc, loader="fabric"):
    q = urllib.parse.urlencode({
        "loaders": json.dumps([loader]),
        "game_versions": json.dumps([mc]),
    })
    versions = get_json(f"https://api.modrinth.com/v2/project/{slug}/version?{q}")
    if not versions:
        sys.exit(f"Нет версии {slug} для {loader} {mc} на Modrinth")
    v = next((v for v in versions if v["version_type"] == "release"), versions[0])
    f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
    print(f"  {slug}: {v['version_number']} ({f['filename']})")
    return {
        "path": f"mods/{f['filename']}",
        "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
        "env": {"client": "required", "server": "unsupported"},
        "downloads": [f["url"]],
        "fileSize": f["size"],
    }


# ---------- servers.dat (несжатый NBT) ----------

def _s(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def servers_dat(name, address):
    out = io.BytesIO()
    out.write(b"\x0a" + _s(""))                       # корневой compound
    out.write(b"\x09" + _s("servers") + b"\x0a" + struct.pack(">i", 1))
    out.write(b"\x08" + _s("name") + _s(name))
    out.write(b"\x08" + _s("ip") + _s(address))
    out.write(b"\x01" + _s("acceptTextures") + b"\x01")
    out.write(b"\x00")                                # конец записи сервера
    out.write(b"\x00")                                # конец корня
    return out.getvalue()


def main():
    mod_jar, out_dir = sys.argv[1], sys.argv[2]
    cfg = json.load(open(os.path.join(ROOT, "config", "serverpack.json"), encoding="utf-8"))
    mc, loader = cfg["minecraft_version"], cfg["fabric_loader"]

    print(f"Сборка модпака: Minecraft {mc}, Fabric {loader}")
    files = [modrinth_file(slug, mc) for slug in cfg["modrinth_mods"]]

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": "1",
        "name": cfg["app_name"],
        "files": files,
        "dependencies": {"minecraft": mc, "fabric-loader": loader},
    }

    srv = cfg["server"]
    jar_bytes = open(mod_jar, "rb").read()

    # Ревизия меняется, если поменялись моды, сервер или наш мод — тогда лаунчер обновит сборку
    rev_src = json.dumps(index, sort_keys=True).encode() + jar_bytes + srv["address"].encode()
    revision = hashlib.sha1(rev_src).hexdigest()[:12]
    index["versionId"] = revision

    os.makedirs(out_dir, exist_ok=True)
    with zipfile.ZipFile(os.path.join(out_dir, "pack.mrpack"), "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=1))
        z.writestr("overrides/mods/mobilecraft.jar", jar_bytes)
        z.writestr("overrides/servers.dat", servers_dat(srv["name"], srv["address"]))

    with open(os.path.join(out_dir, "pack.json"), "w", encoding="utf-8") as f:
        json.dump({
            "server_name": srv["name"],
            "address": srv["address"],
            "revision": revision,
        }, f, ensure_ascii=False)
    print(f"Готово, ревизия {revision}")


if __name__ == "__main__":
    main()
