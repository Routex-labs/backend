# -*- coding: utf-8 -*-
"""`snapshot/`이 가리키는 사진을 클라이언트 번들에 받아 둔다.

오버레이의 `hero.local_asset`·`menu.image_asset`은 **앱 번들 경로**다. 원격 URL을
그대로 적을 수 없어서, 쓰기로 한 사진은 먼저 여기로 받아 놓아야 한다.

    python tools/thehyundai_web/fetch_assets.py <클라이언트 저장소>/client

파일 이름은 원본 URL의 basename(내용 해시)을 그대로 쓴다. 같은 사진을 두 번 받지
않고, 사이트가 사진을 바꾸면 이름이 달라져 눈에 띈다. 사람이 읽을 이름은 오버레이의
`name`이 갖는다.
"""
import json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT = os.path.join(HERE, "snapshot")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/128.0 Safari/537.36")


def asset_name(url, prefix):
    """URL → 번들 파일 이름. `build_overlays.py`가 같은 규칙으로 경로를 적는다."""
    return "%s_%s" % (prefix, os.path.basename(url).split("?")[0])


def download(url, dest):
    if os.path.exists(dest):
        return False
    subprocess.run(["curl", "-sSL", "--fail", "-A", UA, "-o", dest, url], check=True)
    return True


def main(client_root):
    out = os.path.join(client_root, "assets", "place_details")
    os.makedirs(out, exist_ok=True)

    with open(os.path.join(SNAPSHOT, "facilities.json"), encoding="utf-8") as f:
        cards = json.load(f)

    got, skipped, failed = 0, 0, []
    for card in cards:
        url = card.get("image")
        if not url:
            continue
        dest = os.path.join(out, asset_name(url, "facility"))
        try:
            got += download(url, dest)
            skipped += os.path.exists(dest) and 0
        except subprocess.CalledProcessError:
            failed.append(card["name"])
    print("받음 %d건 · 실패 %d건 %s" % (got, len(failed), failed))
    print("저장 위치:", out)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("클라이언트 저장소의 client/ 경로를 인자로 준다.")
    main(sys.argv[1])
