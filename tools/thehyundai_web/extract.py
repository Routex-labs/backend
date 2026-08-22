# -*- coding: utf-8 -*-
"""더현대 서울 공개 웹(thehyundaiseoul.ehyundai.com)에서 매장·시설 상세를 받아 온다.

받는 곳 넷:

  1) `/store/restaurants`            식당 63건. Next.js RSC flight 페이로드에 레코드가
                                     통째로 박혀 있어 HTML 마크업을 파싱할 필요가 없다.
  2) `/api/floor-brands?floorCode=`  층별 브랜드 534건(이름·전화·brandCode·구획).
  3) `/api/dining/menu?...`          식당별 메뉴(이름·설명·가격·사진).
  4) `/about/facilities`             편의시설 19건. 여기만 API가 아니라 RSC 엘리먼트
                                     트리라 카드 단위로 잘라 읽는다.

결과는 `snapshot/`에 그대로 떨어뜨린다. 오버레이로 바꾸는 것은
`build_overlays.py`의 몫이다 — 받아 오는 일과 고르는 일을 한 파일에 섞으면
사이트가 바뀌었을 때 어느 쪽이 깨졌는지 구분되지 않는다.

    python tools/thehyundai_web/extract.py tools/thehyundai_web/snapshot
"""
import json, os, subprocess, sys, time

BASE = "https://thehyundaiseoul.ehyundai.com"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/128.0 Safari/537.36")

# 층 코드는 사이트가 쓰는 값 그대로다. 앞 네 자리 B010은 건물, 뒤가 층이다.
FLOORS = [("B2", "B010B200"), ("B1", "B010B100"), ("1F", "B0100100"), ("2F", "B0100200"),
          ("3F", "B0100300"), ("4F", "B0100400"), ("5F", "B0100500"), ("6F", "B0100600")]


def get(path):
    # 파이썬 배포본에 CA 번들이 없는 환경이 있어 urllib이 막힌다. curl에 맡긴다.
    out = subprocess.run(["curl", "-sSL", "--fail", "-A", UA, BASE + path],
                         capture_output=True, check=True).stdout
    return out.decode("utf-8")


def flight_text(html):
    """`self.__next_f.push([1,"…"])` 조각을 이어붙여 원본 RSC 문자열을 되살린다."""
    marker = 'self.__next_f.push([1,'
    dec, out, i = json.JSONDecoder(), [], 0
    while True:
        i = html.find(marker, i)
        if i < 0:
            return "".join(out)
        j = i + len(marker)
        try:
            chunk, end = dec.raw_decode(html, j)
        except ValueError:
            i = j
            continue
        if isinstance(chunk, str):
            out.append(chunk)
        i = end


def scan_objects(s, key):
    """key가 나오는 지점마다 그것을 감싸는 `{…}`를 괄호 균형으로 잘라 JSON으로 읽는다.

    정규식을 쓰지 않는 이유는 소개글에 중괄호·따옴표가 섞여 있어서다.
    """
    res, i = [], 0
    while True:
        i = s.find(key, i)
        if i < 0:
            return res
        st = s.rfind("{", 0, i)
        depth, j, instr, esc = 0, st, False, False
        while j < len(s):
            c = s[j]
            if instr:
                if esc:
                    esc = False
                elif c == "\\":
                    esc = True
                elif c == '"':
                    instr = False
            else:
                if c == '"':
                    instr = True
                elif c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                    if depth == 0:
                        break
            j += 1
        try:
            res.append(json.loads(s[st:j + 1]))
        except ValueError:
            pass
        i = j + 1


def restaurants():
    s = flight_text(get("/store/restaurants"))
    rows, seen = [], set()
    for o in scan_objects(s, '"lastOrder"'):
        # 같은 키가 i18n 사전에도 있다. seq가 있는 것만 레코드다.
        if "name" not in o or "seq" not in o or o["seq"] in seen:
            continue
        seen.add(o["seq"])
        rows.append({k: (None if v == "$undefined" else v) for k, v in o.items()})
    return rows


def brands():
    rows = []
    for label, code in FLOORS:
        d = json.loads(get("/api/floor-brands?floorCode=%s&locale=ko" % code))
        for sec in d.get("sections", []):
            for b in sec["brands"]:
                tel = b.get("tel")
                rows.append({"floor": label, "floorCode": code, "section": sec["title"],
                             "name": b["name"], "nameKo": b.get("nameKo"),
                             # 전화가 없는 자리를 사이트는 "--"로 채운다.
                             "tel": None if tel in ("--", "", None) else tel,
                             "brandCode": b.get("brandCode")})
        time.sleep(0.3)
    return rows


def menus(rests):
    out = {}
    for r in rests:
        q = "/api/dining/menu?floorCode=%s&diningSeq=%s&locale=ko" % (r["floorCode"], r["seq"])
        try:
            out[r["seq"]] = json.loads(get(q)).get("items", [])
        except (subprocess.CalledProcessError, ValueError) as e:
            out[r["seq"]] = []
            print("  menu fail", r["name"], e, file=sys.stderr)
        time.sleep(0.3)
    return out


def _after(seg, marker, pos=0):
    """marker 바로 뒤 JSON 값을 디코더로 읽는다. 본문에 따옴표가 섞여도 안 잘린다."""
    i = seg.find(marker, pos)
    if i < 0:
        return None
    try:
        return json.JSONDecoder().raw_decode(seg, i + len(marker))[0]
    except ValueError:
        return None


def facilities():
    """`/about/facilities`는 API가 아니라 RSC 엘리먼트 트리다 — 카드(`article`) 단위로 자른다."""
    s = flight_text(get("/about/facilities"))
    rows = []
    for seg in s.split('"$","article",null,')[1:]:
        head = seg.find("font-display")
        name = _after(seg, '"children":', head) if head >= 0 else None
        if not isinstance(name, str):
            continue
        img = _after(seg, '"src":')
        floor = _after(seg, '"dd",null,{"children":')
        desc = _after(seg, 'whitespace-pre-line","children":')
        rows.append({"name": name.strip(),
                     "floor": floor.strip() if isinstance(floor, str) else None,
                     "description": desc.strip() if isinstance(desc, str) else None,
                     "image": img if isinstance(img, str) and img.startswith("http") else None,
                     "imageAlt": _after(seg, '"alt":')})
    return rows


def main(dst):
    os.makedirs(dst, exist_ok=True)

    def dump(name, obj):
        with open(os.path.join(dst, name), "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=1, sort_keys=True)
        print(name, len(obj))

    rests = restaurants()
    dump("restaurants.json", rests)
    dump("brands.json", brands())
    m = menus(rests)
    dump("menus.json", m)
    print("  menu items", sum(len(v) for v in m.values()))
    dump("facilities.json", facilities())


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "snapshot")
