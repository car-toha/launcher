# MobileCraft

Android-лаунчер одного сервера. Неофициальная модифицированная версия
[Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) (движок PojavLauncher), GPL-3.0.

**Внутри:** Minecraft 26.1.2 + Fabric, Simple Voice Chat, Litematica (+MaLiLib) и мод MobileCraft.
**Вход:** по нику (сервер в offline-режиме), без аккаунта Microsoft.

## Как это работает

- Экран приложения: поле «Ник» и «Играть». При первом нажатии ставится сборка, затем
  игра сразу подключается к серверу. «Настройки и управление» открывают полный интерфейс Zalith.
- Сборка — модпак `.mrpack`, его собирает CI: свежие версии модов под 26.1.2 с Modrinth,
  мод MobileCraft и `servers.dat`. Если сборка поменялась, приложение обновит её, сохранив
  миры, настройки и схемы Litematica.
- Мод MobileCraft (`mod/`): чат меньше и сверху по центру; F13 — повтор последней команды;
  F14 — микрофон вкл/выкл (Simple Voice Chat в режиме PTT); индикатор «MIC».
- Раскладка (`tools/make_layout.py`, схема в `launcher/CONTROLS.md`): джойстик, прыжок, присед-переключатель,
  жесты вместо кнопок ЛКМ/ПКМ, кнопки 💬 ↻ 🎤 у чата.

## Настройка

`config/serverpack.json` — название и адрес сервера, версии, список модов с Modrinth.

## Сборка

Любой push в `main` запускает Actions → **Build MobileCraft**. Готовый APK — в артефакте
**MobileCraft-apk** (под arm64, это почти все телефоны).

## Структура

```
config/serverpack.json     сервер и состав сборки
mod/                       Fabric-мод MobileCraft
tools/make_mrpack.py       сборка модпака
tools/make_layout.py       раскладка управления
launcher/zalith/           правки Zalith: экран MobileCraft (MobileCraft.kt) и patch.py
```
