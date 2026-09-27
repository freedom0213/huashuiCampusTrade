#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""幂等导入 Nacos 配置（同 dataId 直接覆盖为仓库里的内容）。

用法：python docs/nacos/import-configs.py   （仅用标准库，无需装第三方包）
前提：Nacos 已启动（http://127.0.0.1:18848，本地部署未开启鉴权）。

为什么用 python 而不是 bash + curl：
  Windows 下 Git Bash 把命令行参数里的中文按 GBK 转码（本机 locale 是 CP936），
  curl 收到的 content 已经是坏字节，Nacos 存进去的 yml 无法按 UTF-8 解析，
  客户端拉到后 SnakeYAML 直接 MalformedInputException —— 与 docs/sql 里
  「mysql 客户端默认 gkb 导致乱码」是同一类坑，这次在导入环节就绕开。
  python 的 urllib 全程 UTF-8，没有这个环节。

为什么做成脚本而不是在控制台里点：与 docs/sql/ 同一个理由——
配置内容进 git、可被 review、别人克隆后跑一遍即可用，
「仓库里的这份」就是所有 Nacos 配置的唯一事实来源。

想临时调参数（演示热更新）就直接在 Nacos 控制台改，改完记得
把新值同步回这里并提交，否则下次跑脚本会把控制台的修改冲掉。
"""

import glob
import os
import sys
import urllib.parse
import urllib.request

NACOS = os.environ.get("NACOS_ADDR", "http://127.0.0.1:18848")
GROUP = "HUASHUI_GROUP"
DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "configs")


def post_config(data_id, group, content, config_type):
    """Nacos v1 OpenAPI。python 的 urlencode 全程 UTF-8，中文安全。"""
    data = urllib.parse.urlencode({
        "dataId": data_id,
        "group": group,
        "type": config_type,
        "content": content,
    }).encode("utf-8")
    req = urllib.request.Request(
        f"{NACOS}/nacos/v1/cs/configs",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return resp.read().decode("utf-8", "ignore").strip()


def main():
    fail = 0
    files = sorted(glob.glob(os.path.join(DIR, "*.yml"))) + sorted(
        glob.glob(os.path.join(DIR, "*.json"))
    )
    for path in files:
        name = os.path.basename(path)
        config_type = "json" if name.endswith(".json") else "yaml"
        with open(path, encoding="utf-8") as fh:
            content = fh.read()
        try:
            resp = post_config(name, GROUP, content, config_type)
        except Exception as e:  # noqa: BLE001
            resp = f"EXCEPTION {type(e).__name__}: {e}"
        if resp == "true":
            print(f"OK    {name}")
        else:
            print(f"FAIL  {name} -> {resp}")
            fail = 1
    if fail:
        print(f"存在失败项，请检查 Nacos 是否可访问：{NACOS}", file=sys.stderr)
        sys.exit(1)
    print(f"全部配置已导入 Nacos（group={GROUP}，共 {len(files)} 项）")


if __name__ == "__main__":
    main()
