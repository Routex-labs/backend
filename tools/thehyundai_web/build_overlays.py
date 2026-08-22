# -*- coding: utf-8 -*-
"""`snapshot/`의 사이트 데이터를 `store_details/` 오버레이로 바꾼다.

조인 키는 매장 id다. 사이트는 id를 모르므로 배포된 백엔드의 store-index에서
(층, 이름) → id를 얻어 붙인다.

    python tools/thehyundai_web/build_overlays.py

**무엇을 싣고 무엇을 버리는지가 이 파일의 요점이다.** `_schema.json`이 막는 것과
낡는 것을 여기서 걸러 낸다.

  싣는다  summary  식당·시설 소개글. `※`로 시작하는 운영 고지는 잘라 낸다 —
                  "LAST ORDER 19:20" 같은 값이 소개글에 섞여 들어오는데, 소개는
                  안 낡고 그 줄은 낡는다.
          hours    "10:30 ~ 20:00"과 브레이크타임을 요일 7개 구조체로 편다.
                   `_schema.json`이 자유 문자열 영업시간을 막는 대신 낸 길이다.
          source   그 문구가 실제로 적혀 있던 페이지.

  버린다  전화번호  `forbidden_labels`가 keyValue·businessInfo 양쪽에서 막는다.
                   구조체 자리도 없다. 534건을 받아 두고도 싣지 않는 이유다.
          lastOrder  같은 이유. hours 구조체에 라스트오더 자리가 없다.
          사진·메뉴   `hero.local_asset`·`menu.image_asset`은 앱 번들 경로여서
                   원격 URL을 그대로 넣을 수 없다. 이미지를 클라이언트
                   저장소에 받아 넣는 것은 별도 작업이다.
          휴점일     사이트가 요일 규칙으로 공지하지 않는다. 요일에서 유추하면
                   확인되지 않은 값이 확인된 값과 같은 모양으로 저장된다
                   (`_schema.json`의 exceptions_upkeep_note와 같은 판단).
"""
import json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT = os.path.join(HERE, "snapshot")
OUT = os.path.abspath(os.path.join(HERE, "..", "..", "src", "main", "resources", "store_details"))

API = "https://navigation-api-465890645804.asia-northeast3.run.app"
BUILDING = "thehyundai-seoul"
RESTAURANTS_URL = "https://thehyundaiseoul.ehyundai.com/store/restaurants"
FACILITIES_URL = "https://thehyundaiseoul.ehyundai.com/about/facilities"
KST_OFFSET_MIN = 540

WEEKDAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]

# 사이트 이름과 도면 이름이 다른 것들. 자동 매칭이 닿지 않아 사람이 정한 짝이다.
# 왼쪽이 사이트, 오른쪽이 도면(store-index) 이름.
RESTAURANT_ALIASES = {
    "강호연파": "강호연파 밥굽남",
    "와인웍스": "와인웍스(다이닝)",
    "쎄띠엠므": "세띠 엠므",
    "블루보틀 커피": "블루보틀",
    "FIVE GUYS": "파이브 가이즈",
}
FACILITY_ALIASES = {
    "시계 수선실": "타임 닥터",
    "수선실(의류)": "아뜰리에 아티산 (의류 수선)",
    "사은데스크": "사은",
    "PETIT LOUNGE (유아휴게실)": "쁘띠 라운지 (유아휴게실)",
    "PETIT ROOM (수유실)": "수유실",
    "상품권 데스크": "상품권(서비스 라운지 안)",
}


def norm(s):
    return re.sub(r"[\s\-·/()\.]+", "", s).lower()


def store_index():
    out = subprocess.run(
        ["curl", "-sSL", "--fail", "%s/buildings/%s/store-index" % (API, BUILDING)],
        capture_output=True, check=True).stdout
    return json.loads(out.decode("utf-8"))


OUTPUT_FILES = ("thehyundai-restaurants.json", "thehyundai-facilities.json")


def existing_ids():
    """이미 손으로 써 둔 오버레이의 id.

    로더는 같은 id가 두 파일에 있으면 나중 파일이 이긴다 — 사진·메뉴까지 채워 둔
    수작업 오버레이를 소개글만 있는 자동 생성본이 덮을 수 있다. 겹치면 **자동
    생성 쪽을 비운다**: 사람이 더 많이 아는 쪽이 이겨야 한다.
    """
    ids = set()
    if not os.path.isdir(OUT):
        return ids
    for name in sorted(os.listdir(OUT)):
        if not name.endswith(".json") or name.startswith("_") or name in OUTPUT_FILES:
            continue
        with open(os.path.join(OUT, name), encoding="utf-8") as f:
            ids.update(json.load(f))
    return ids


def load(name):
    with open(os.path.join(SNAPSHOT, name), encoding="utf-8") as f:
        return json.load(f)


def clean_summary(text):
    """소개글에서 운영 고지(`※` 이후)를 떼고 빈 줄을 정리한다."""
    if not text:
        return None
    body = text.split("※")[0]
    body = re.sub(r"\n{2,}", "\n", body).strip()
    return body or None


def parse_span(text):
    """'10:30 ~ 20:00' → ('10:30', '20:00'). 형식이 다르면 None."""
    if not text:
        return None
    m = re.match(r"^\s*(\d{1,2}:\d{2})\s*~\s*(\d{1,2}:\d{2})\s*$", text)
    if not m:
        return None
    return m.group(1).zfill(5), m.group(2).zfill(5)


def intervals(hours, break_time):
    """영업시간과 브레이크타임을 오름차순·비중첩 구간 배열로 편다."""
    span = parse_span(hours)
    if not span:
        return None
    open_at, close_at = span
    if open_at == close_at:
        return None
    brk = parse_span(break_time)
    if not brk:
        return [{"open": open_at, "close": close_at}]
    b_start, b_end = brk
    # 브레이크가 영업시간 밖이면 쪼개지 않는다 — 억지로 나누면 없는 구간이 생긴다.
    if not (open_at < b_start < b_end < close_at):
        return [{"open": open_at, "close": close_at}]
    return [{"open": open_at, "close": b_start}, {"open": b_end, "close": close_at}]


def build_hours(rest, today):
    ivs = intervals(rest.get("hours"), rest.get("breakTime"))
    if not ivs:
        return None
    return {"weekly": {d: [dict(i) for i in ivs] for d in WEEKDAYS},
            "utc_offset_minutes": KST_OFFSET_MIN,
            "confirmed_at": today,
            "source": RESTAURANTS_URL}


def match_restaurants(rests, stores):
    """(층, 이름)으로 먼저 붙이고, 남은 것은 별칭으로 붙인다."""
    by_floor_name = {}
    by_name = {}
    for e in stores:
        by_floor_name.setdefault((e["floor_name"], norm(e["name"])), e)
        by_name.setdefault(norm(e["name"]), e)
    pairs, missed = [], []
    for r in rests:
        floor = "B1" if r.get("floor", "").startswith("지하") else (r.get("floor", "").replace("층", "F"))
        target = RESTAURANT_ALIASES.get(r["name"], r["name"])
        hit = by_floor_name.get((floor, norm(target))) or by_name.get(norm(target))
        if hit:
            pairs.append((hit, r))
        else:
            missed.append(r["name"])
    return pairs, missed


def match_facilities(cards, facs, stores):
    """시설 이름은 도면 쪽 표기가 들쭉날쭉해(`물 품 보 관 함`) 포함 관계까지 본다.

    한 카드가 여러 층의 같은 시설에 붙는 것은 정상이다(수유실·물품보관함).
    도면이 시설이 아니라 매장으로 분류한 것도 있어(CH 1985) 매장까지 훑는다.
    """
    pairs, missed = [], []
    pool = list(facs) + list(stores)
    for c in cards:
        target = norm(FACILITY_ALIASES.get(c["name"], c["name"]))
        hits = [e for e in pool if norm(e["name"]) == target]
        if not hits:
            # 포함 관계는 시설 쪽에서만 본다 — 매장 이름은 짧아 엉뚱하게 걸린다.
            hits = [e for e in facs
                    if target and (target in norm(e["name"]) or norm(e["name"]) in target)]
        if hits:
            pairs.append((hits, c))
        else:
            missed.append(c["name"])
    return pairs, missed


def main(today):
    idx = store_index()
    stores = [e for e in idx if e.get("kind") == "store"]
    facs = [e for e in idx if e.get("kind") == "facility"]

    rests = load("restaurants.json")
    cards = load("facilities.json")

    taken = existing_ids()
    r_pairs, r_missed = match_restaurants(rests, stores)
    f_pairs, f_missed = match_facilities(cards, facs, stores)

    out_rest = {}
    no_summary, no_hours, skipped = [], [], []
    for store, r in r_pairs:
        if store["id"] in taken:
            skipped.append(store["name"])
            continue
        overlay = {"name": store["name"], "updated_at": today}
        summary = clean_summary(r.get("description"))
        if summary:
            overlay["summary"] = summary
            overlay["source"] = RESTAURANTS_URL
        else:
            no_summary.append(store["name"])
        hours = build_hours(r, today)
        if hours:
            overlay["hours"] = hours
        else:
            no_hours.append(store["name"])
        if len(overlay) > 2:
            out_rest[store["id"]] = overlay

    out_fac = {}
    for hits, c in f_pairs:
        summary = clean_summary(c.get("description"))
        if not summary:
            continue
        for e in hits:
            if e["id"] in taken:
                skipped.append(e["name"])
                continue
            out_fac[e["id"]] = {"name": e["name"], "updated_at": today,
                                "summary": summary, "source": FACILITIES_URL}

    os.makedirs(OUT, exist_ok=True)
    for name, payload in (("thehyundai-restaurants.json", out_rest),
                          ("thehyundai-facilities.json", out_fac)):
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write("\n")
        print(name, len(payload))

    print("식당 매칭 %d/%d  못 붙음: %s" % (len(r_pairs), len(rests), r_missed))
    print("시설 매칭 %d/%d  못 붙음: %s" % (len(f_pairs), len(cards), f_missed))
    if no_summary:
        print("소개글 없음:", no_summary)
    if no_hours:
        print("영업시간 못 읽음:", no_hours)
    if skipped:
        print("수작업 오버레이가 이미 있어 건너뜀 %d건: %s" % (len(skipped), skipped))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("확인일(YYYY-MM-DD)을 인자로 준다 — 오늘 날짜를 코드가 멋대로 정하지 않는다.")
    main(sys.argv[1])
