#!/usr/bin/env python3
"""
Превращает исходники Zalith Launcher 2 в MobileCraft.

  python3 launcher/zalith/patch.py <zalith_dir> <pack_dir>

pack_dir — результат tools/make_mrpack.py (pack.mrpack, pack.json).
Каждая правка проверяется: если в новой версии Zalith код поменялся и якорь
не найден, скрипт падает с понятной ошибкой, а не собирает сломанный APK.
"""
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

zl, pack_dir = sys.argv[1], sys.argv[2]
app = os.path.join(zl, "ZalithLauncher")
src = os.path.join(app, "src", "main")
pkg = os.path.join(src, "java", "com", "movtery", "zalithlauncher")


def edit(path, pairs, regex=False):
    full = os.path.join(zl, path) if not os.path.isabs(path) else path
    text = open(full, encoding="utf-8").read()
    for old, new in pairs:
        if regex:
            text, n = re.subn(old, new, text, count=1, flags=re.M)
        else:
            n = text.count(old)
            text = text.replace(old, new, 1)
        if n == 0:
            sys.exit(f"PATCH FAILED: не найден фрагмент в {path}:\n{old}")
    open(full, "w", encoding="utf-8").write(text)
    print(f"  ✓ {path}")


print("Название и пакет приложения")
edit("ZalithLauncher/gradle.properties", [
    (r"^launcher_name=.*$", "launcher_name=MobileCraft"),
    (r"^launcher_app_name=.*$", "launcher_app_name=MobileCraft"),
    (r"^launcher_short_name=.*$", "launcher_short_name=MBC"),
    (r"^url_home=.*$", "url_home=https://github.com/car-toha/launcher"),
], regex=True)
edit("ZalithLauncher/build.gradle.kts", [
    ("applicationId = zalithPackageName", 'applicationId = "ru.mobilecraft.launcher"'),
    ('applicationIdSuffix = ".v2"', ""),
    ('applicationIdSuffix = ".debug"', ""),
])

print("Свой главный экран")
os.makedirs(os.path.join(pkg, "mobilecraft"), exist_ok=True)
shutil.copy(os.path.join(HERE, "MobileCraft.kt"), os.path.join(pkg, "mobilecraft", "MobileCraft.kt"))

MA = "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/activities/MainActivity.kt"
edit(MA, [
    ("                    MainScreen(\n",
     "                    if (com.movtery.zalithlauncher.mobilecraft.MobileCraft.showFullLauncher.value) MainScreen(\n"),
    ("                    )\n\n                    //节日彩蛋效果层",
     "                    ) else com.movtery.zalithlauncher.mobilecraft.MobileHome(\n"
     "                        this@MainActivity, launchGameViewModel, modpackImportViewModel\n"
     "                    )\n"
     "                    com.movtery.zalithlauncher.mobilecraft.MobileBackButton()\n\n"
     "                    //节日彩蛋效果层"),
    # Обновления и спонсоры оригинального Zalith здесь не нужны
    ("launcherUpgradeViewModel.checkOnAppStart()", "Unit"),
    ("showLastMonth = GuideProgress.isPlayed(GuideKeys.Main) && !isImporting", "showLastMonth = false"),
])

print("Тихий импорт модпака (без вопросов об имени версии)")
edit("ZalithLauncher/src/main/java/com/movtery/zalithlauncher/viewmodel/ModpackImportViewModel.kt", [
    ("    suspend fun waitForVersionName(name: String): String {\n",
     "    suspend fun waitForVersionName(name: String): String {\n"
     "        com.movtery.zalithlauncher.mobilecraft.MobileCraft.autoVersionName?.let { return it }\n"),
    ("    suspend fun waitForConfirmMobileData(): Boolean {\n",
     "    suspend fun waitForConfirmMobileData(): Boolean {\n"
     "        if (com.movtery.zalithlauncher.mobilecraft.MobileCraft.autoVersionName != null) return true\n"),
])

print("Сборка и раскладка в assets")
assets = os.path.join(src, "assets")
os.makedirs(os.path.join(assets, "mobilecraft"), exist_ok=True)
for name in ("pack.mrpack", "pack.json"):
    shutil.copy(os.path.join(pack_dir, name), os.path.join(assets, "mobilecraft", name))
layout = os.path.join(assets, "default_layout.json")
subprocess.run([sys.executable, os.path.join(ROOT, "tools", "make_layout.py"), layout, layout], check=True)

print("Готово")
