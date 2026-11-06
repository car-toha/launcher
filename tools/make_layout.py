#!/usr/bin/env python3
"""
Делает раскладку MobileCraft из стандартной раскладки Zalith.

  python3 tools/make_layout.py <zalith default_layout.json> <out.json>

Координаты Zalith: x, y от 0 до 10000 (доля свободного места экрана),
размеры — в сотых долях процента от высоты экрана.
От стандартной раскладки берём джойстик, прыжок, присед-переключатель и меню (GUI),
убираем кнопки ЛКМ/ПКМ (как в Bedrock: тап — поставить, удержание — ломать)
и добавляем у чата наверху «Чат», «Повтор команды» (F13) и «Микрофон» (F14).
"""
import json
import sys
import uuid


def uid(n=18):
    return uuid.uuid4().hex[:n]


def button(text, x, y, events, style, size=1100, toggle=False):
    return {
        "text": {"default": text, "matchQueue": []},
        "uuid": uid(),
        "position": {"x": x, "y": y},
        "buttonSize": {
            "type": "percentage",
            "widthDp": 50.0, "heightDp": 50.0,
            "widthPercentage": size, "heightPercentage": size,
            "widthReference": "screen_height", "heightReference": "screen_height",
        },
        "buttonStyle": style,
        "visibilityType": "in_game",
        "clickEvents": events,
        "isSwipple": False,
        "isPenetrable": False,
        "isToggleable": toggle,
    }


def key(name):
    return [{"type": "key", "key": name}]


def main():
    src, out = sys.argv[1], sys.argv[2]
    d = json.load(open(src, encoding="utf-8"))

    d["info"]["name"] = {"default": "MobileCraft", "matchQueue": []}
    d["info"]["author"] = {"default": "MobileCraft", "matchQueue": []}
    d["info"]["description"] = {"default": "Управление в стиле Bedrock", "matchQueue": []}

    styles = [s["uuid"] for s in d["styles"]]
    style = "d1096cf91caa" if "d1096cf91caa" in styles else styles[0]

    for layer in d["layers"]:
        btns = layer.get("normalButtons")
        if not btns:
            continue
        # Убираем ЛКМ/ПКМ: в Bedrock это жесты по экрану
        layer["normalButtons"] = [
            b for b in btns
            if not any(e.get("key") in ("GLFW_MOUSE_BUTTON_LEFT", "GLFW_MOUSE_BUTTON_RIGHT")
                       for e in b["clickEvents"])
        ]
        # Прыжок и присед делаем крупнее — под большой палец
        for b in layer["normalButtons"]:
            keys = {e.get("key") for e in b["clickEvents"]}
            if keys & {"GLFW_KEY_SPACE", "GLFW_KEY_LEFT_SHIFT"}:
                b["buttonSize"]["widthPercentage"] = 1700
                b["buttonSize"]["heightPercentage"] = 1700
            if "GLFW_KEY_SPACE" in keys:
                b["position"] = {"x": 9600, "y": 8600}
            if "GLFW_KEY_LEFT_SHIFT" in keys:
                b["position"] = {"x": 8350, "y": 9300}

    small = 1000
    d["layers"].append({
        "name": "mobilecraft",
        "uuid": uid(12),
        "hide": False,
        "visibilityType": "in_game",
        "normalButtons": [
            # Ряд у чата (чат мод MobileCraft рисует сверху по центру)
            button("💬", 2700, 0, key("GLFW_KEY_T"), style, small),
            button("↻", 7050, 0, key("GLFW_KEY_F13"), style, small),
            button("🎤", 7550, 0, key("GLFW_KEY_F14"), style, small),
            # Справа сверху: меню Litematica и смена вида
            button("M", 10000, 1300, key("GLFW_KEY_M"), style, small),
            button("👁", 10000, 2500, key("GLFW_KEY_F5"), style, small),
            # Над прыжком: выбросить предмет
            button("Q", 9750, 6000, key("GLFW_KEY_Q"), style, small),
        ],
    })

    json.dump(d, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("Раскладка MobileCraft готова")


if __name__ == "__main__":
    main()
