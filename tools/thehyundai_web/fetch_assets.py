# -*- coding: utf-8 -*-
"""`snapshot/`이 가리키는 사진을 클라이언트 번들에 받아 둔다.

오버레이의 `hero.local_asset`·`menu.image_asset`은 **앱 번들 경로**다. 원격 URL을
그대로 적을 수 없어서, 쓰기로 한 사진은 먼저 여기로 받아 놓아야 한다.

    python tools/thehyundai_web/fetch_assets.py <Routee>/client

**원본을 그대로 받지 않는다.** 원본에 4000×2667짜리가 섞여 있는데, 화면은 그 크기를
쓰지 않으면서 디코딩에만 40MB 넘게 먹는다. 사이트의 이미지 프록시(`/_next/image`)에
`w=1080`으로 요청해 기존 번들 사진(1080px 안팎)과 규격을 맞춘다. 프록시는 **키우지는
않으므로** 원본이 작으면 그대로 온다.

파일 이름은 원본 URL의 basename(내용 해시)을 그대로 쓴다. 같은 사진을 두 번 받지
않고, 사이트가 사진을 바꾸면 이름이 달라져 눈에 띈다. 사람이 읽을 이름은 오버레이의
`name`이 갖는다.
"""
import json, os, subprocess, sys, urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT = os.path.join(HERE, "snapshot")
RESIZER = "https://thehyundaiseoul.ehyundai.com/_next/image"
TARGET_WIDTH = 1080
QUALITY = 75
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/128.0 Safari/537.36")


def asset_name(url, prefix):
    """URL → 번들 파일 이름. `build_overlays.py`가 같은 규칙으로 경로를 적는다."""
    return "%s_%s" % (prefix, os.path.basename(urllib.parse.urlparse(url).path))


def resized(url):
    return "%s?url=%s&w=%d&q=%d" % (
        RESIZER, urllib.parse.quote(url, safe=""), TARGET_WIDTH, QUALITY)


def download(url, dest):
    if os.path.exists(dest):
        return False
    subprocess.run(["curl", "-sSL", "--fail", "-A", UA, "-o", dest, resized(url)],
                   check=True)
    return True


def targets():
    """(원본 URL, 파일 이름 접두) 목록. 같은 URL이 두 번 나와도 이름이 같아 한 번만 받는다."""
    def load(name):
        with open(os.path.join(SNAPSHOT, name), encoding="utf-8") as f:
            return json.load(f)

    for card in load("facilities.json"):
        if card.get("image"):
            yield card["image"], "facility"
    for rest in load("restaurants.json"):
        for url in rest.get("images") or []:
            yield url, "dining"
    for items in load("menus.json").values():
        for item in items:
            if item.get("image"):
                yield item["image"], "menu"


def main(client_root):
    out = os.path.join(client_root, "assets", "place_details")
    os.makedirs(out, exist_ok=True)

    got, have, failed = 0, 0, []
    for url, prefix in targets():
        dest = os.path.join(out, asset_name(url, prefix))
        try:
            if download(url, dest):
                got += 1
            else:
                have += 1
        except subprocess.CalledProcessError:
            failed.append(url)
    print("새로 받음 %d · 이미 있음 %d · 실패 %d" % (got, have, len(failed)))
    for url in failed:
        print("  실패:", url)
    print("저장 위치:", out)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("클라이언트 저장소의 client/ 경로를 인자로 준다.")
    main(sys.argv[1])
