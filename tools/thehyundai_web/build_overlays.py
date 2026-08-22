# -*- coding: utf-8 -*-
"""`snapshot/`의 사이트 데이터를 `store_details/` 오버레이로 바꾼다.

조인 키는 매장 id다. 사이트는 id를 모르므로 배포된 백엔드의 store-index에서
(층, 이름) → id를 얻어 붙인다.

    python tools/thehyundai_web/build_overlays.py 2026-08-22

**무엇을 싣고 무엇을 버리는지가 이 파일의 요점이다.** `_schema.json`이 막는 것과
낡는 것을 여기서 걸러 낸다.

  싣는다  summary  식당·시설 소개글. `※` 뒤 운영 고지는 잘라 낸다 — "LAST ORDER
                  19:20" 같은 값이 소개글에 섞여 들어오는데, 소개는 안 낡고 그
                  줄은 낡는다.
          hero     시설 사진. `fetch_assets.py`가 번들에 받아 둔 파일을 가리킨다.
          hours    "10:30 ~ 20:00"과 브레이크타임을 요일 7개 구조체로 편다.
          contact  전화번호. 스키마 v5에서 열린 구조체다 — 자유 문자열 "전화번호"는
                  여전히 금지이고, source·confirmed_at을 붙여야만 나간다.
          source   그 문구가 실제로 적혀 있던 페이지.

  버린다  lastOrder  `hours` 구조체에 자리가 없다. 자유 문자열로 넣으면
                   `forbidden_labels`가 막는다.
          식당 사진·메뉴  같은 규칙이지만 아직 안 받았다. 식당 63곳의 사진과
                   메뉴 사진 173장이라 번들이 그만큼 커진다 — 받을지는 따로 정한다.
          휴점일     사이트가 요일 규칙으로 공지하지 않는다. 요일에서 유추하면
                   확인되지 않은 값이 확인된 값과 같은 모양으로 저장된다
                   (`_schema.json`의 exceptions_upkeep_note와 같은 판단).

**한 id는 한 파일에만 쓴다.** 로더(`PlaceOverlays`)는 같은 id가 두 파일에 있으면
나중 파일이 **통째로** 이긴다 — 병합이 아니다. 식당이 층별 브랜드 목록에도 들어 있어,
그대로 두면 소개글·영업시간이 전화번호만 있는 오버레이에 덮인다.
"""
import json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SNAPSHOT = os.path.join(HERE, "snapshot")
OUT = os.path.abspath(os.path.join(HERE, "..", "..", "src", "main", "resources", "store_details"))

API = "https://navigation-api-465890645804.asia-northeast3.run.app"
BUILDING = "thehyundai-seoul"
RESTAURANTS_URL = "https://thehyundaiseoul.ehyundai.com/store/restaurants"
FLOOR_GUIDE_URL = "https://thehyundaiseoul.ehyundai.com/store/floor-guide"
FACILITIES_URL = "https://thehyundaiseoul.ehyundai.com/about/facilities"
KST_OFFSET_MIN = 540

WEEKDAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]

RESTAURANTS_FILE = "thehyundai-restaurants.json"
BRANDS_FILE = "thehyundai-brands.json"
FACILITIES_FILE = "thehyundai-facilities.json"
OUTPUT_FILES = (RESTAURANTS_FILE, BRANDS_FILE, FACILITIES_FILE)

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


def existing_overlays():
    """이미 손으로 써 둔 오버레이. id → 그 오버레이가 연락처를 이미 담았나.

    사진·메뉴까지 채운 수작업 오버레이를 자동 생성본이 덮지 않게 막는다.
    겹치면 **자동 생성 쪽을 비운다**: 사람이 더 많이 아는 쪽이 이겨야 한다.
    연락처 유무까지 보는 것은, 사람이 손으로 채워야 할 곳만 로그에 남기기 위해서다.
    """
    found = {}
    if not os.path.isdir(OUT):
        return found
    for name in sorted(os.listdir(OUT)):
        if not name.endswith(".json") or name.startswith("_") or name in OUTPUT_FILES:
            continue
        with open(os.path.join(OUT, name), encoding="utf-8") as f:
            for place_id, overlay in json.load(f).items():
                found[place_id] = bool(overlay.get("contact"))
    return found


def load(name):
    with open(os.path.join(SNAPSHOT, name), encoding="utf-8") as f:
        return json.load(f)


def clean_summary(text):
    """소개글에서 운영 고지(`※` 이후)를 떼고 빈 줄을 정리한다."""
    if not text:
        return None
    body = re.sub(r"\n{2,}", "\n", text.split("※")[0]).strip()
    return body or None


# 지역번호형(02-3277-0132)과 대표번호형(1522-3232) 둘만 받는다.
TEL_SHAPES = (re.compile(r"^\d{2,4}-\d{3,4}-\d{4}$"), re.compile(r"^\d{4}-\d{4}$"))


def clean_tel(tel):
    """사이트 표기를 그대로 두되 앞뒤 구분기호와 사이 공백만 다듬는다.

    대표번호를 `1522-3232-`처럼 줄표를 달고 주는데, 그대로 실으면 화면이 걸 수 없는
    번호가 된다. 반대로 `02-3277- 070`처럼 자릿수가 모자란 것은 **고치지 않고 버린다** —
    무엇이 빠졌는지 모르는 채 채우면 없는 번호를 만들어 낸다.
    """
    if not tel:
        return None
    cleaned = re.sub(r"\s+", "", tel).strip("-").strip()
    return cleaned if any(shape.match(cleaned) for shape in TEL_SHAPES) else None


def hero(url):
    """번들에 받아 둔 사진 경로. 이름 규칙은 `fetch_assets.py`와 같아야 한다 —
    갈라지면 등록은 됐는데 카드만 빈 채로 뜨고, 빌드는 성공한다."""
    if not url:
        return None
    name = "facility_%s" % os.path.basename(url).split("?")[0]
    return [{"local_asset": "assets/place_details/%s" % name}]


def contact(tel, source, today):
    """전화번호 구조체. 번호로 읽히지 않으면 키 자체를 만들지 않는다."""
    cleaned = clean_tel(tel)
    if not cleaned:
        return None
    return {"tel": cleaned, "confirmed_at": today, "source": source}


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


def index_stores(stores):
    by_floor_name, by_name = {}, {}
    for e in stores:
        by_floor_name.setdefault((e["floor_name"], norm(e["name"])), e)
        by_name.setdefault(norm(e["name"]), e)
    return by_floor_name, by_name


def match_restaurants(rests, stores):
    """(층, 이름)으로 먼저 붙이고, 남은 것은 별칭으로 붙인다."""
    by_floor_name, by_name = index_stores(stores)
    pairs, missed = [], []
    for r in rests:
        floor = r.get("floor", "")
        floor = "B1" if floor.startswith("지하") else floor.replace("층", "F")
        target = RESTAURANT_ALIASES.get(r["name"], r["name"])
        hit = by_floor_name.get((floor, norm(target))) or by_name.get(norm(target))
        pairs.append((hit, r)) if hit else missed.append(r["name"])
    return pairs, missed


def match_brands(brands, stores):
    """브랜드는 층까지 맞춘다 — 같은 이름이 여러 층에 있다."""
    by_floor_name, _ = index_stores(stores)
    pairs, missed = [], []
    for b in brands:
        hit = by_floor_name.get((b["floor"], norm(b["name"])))
        pairs.append((hit, b)) if hit else missed.append(b["name"])
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
        pairs.append((hits, c)) if hits else missed.append(c["name"])
    return pairs, missed


def main(today):
    idx = store_index()
    stores = [e for e in idx if e.get("kind") == "store"]
    facs = [e for e in idx if e.get("kind") == "facility"]

    rests, brands, cards = load("restaurants.json"), load("brands.json"), load("facilities.json")
    r_pairs, r_missed = match_restaurants(rests, stores)
    b_pairs, b_missed = match_brands(brands, stores)
    f_pairs, f_missed = match_facilities(cards, facs, stores)

    # 손으로 쓴 오버레이가 먼저다. 그다음은 이 안에서 먼저 쓴 파일이 자리를 잡는다.
    handwritten = existing_overlays()
    used = set(handwritten)
    # 건너뛴 이유를 갈라 둔다. "이번에 이미 썼다"는 정상이고(식당이 브랜드 목록에도
    # 있다), "수작업본이 있다"만 사람이 볼 일이 남은 쪽이다.
    blocked_by_hand, already_written = [], []

    def claim(place_id, name):
        if place_id in handwritten:
            # 이미 연락처가 있는 수작업본은 남길 일이 없다.
            if not handwritten[place_id]:
                blocked_by_hand.append(name)
            return False
        if place_id in used:
            already_written.append(name)
            return False
        used.add(place_id)
        return True

    out_rest, no_summary, no_hours = {}, [], []
    for store, r in r_pairs:
        if not claim(store["id"], store["name"]):
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
        tel = contact(r.get("tel"), RESTAURANTS_URL, today)
        if tel:
            overlay["contact"] = tel
        if len(overlay) > 2:
            out_rest[store["id"]] = overlay

    out_fac = {}
    for hits, c in f_pairs:
        summary = clean_summary(c.get("description"))
        if not summary:
            continue
        for e in hits:
            if not claim(e["id"], e["name"]):
                continue
            overlay = {"name": e["name"], "updated_at": today,
                       "summary": summary, "source": FACILITIES_URL}
            photo = hero(c.get("image"))
            if photo:
                overlay["hero"] = photo
            out_fac[e["id"]] = overlay

    # 브랜드는 전화번호뿐이다 — 소개글은 현대 쪽에 아예 없다(README 참고).
    out_brand, no_tel = {}, []
    for store, b in b_pairs:
        tel = contact(b.get("tel"), FLOOR_GUIDE_URL, today)
        if not tel:
            no_tel.append(store["name"])
            continue
        if not claim(store["id"], store["name"]):
            continue
        out_brand[store["id"]] = {"name": store["name"], "updated_at": today, "contact": tel}

    os.makedirs(OUT, exist_ok=True)
    for name, payload in ((RESTAURANTS_FILE, out_rest), (FACILITIES_FILE, out_fac),
                          (BRANDS_FILE, out_brand)):
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write("\n")
        print(name, len(payload))

    print("식당 매칭 %d/%d  못 붙음: %s" % (len(r_pairs), len(rests), r_missed))
    print("시설 매칭 %d/%d  못 붙음: %s" % (len(f_pairs), len(cards), f_missed))
    print("브랜드 매칭 %d/%d  (도면에 없는 것 %d건)" % (len(b_pairs), len(brands), len(b_missed)))
    if no_summary:
        print("소개글 없음:", no_summary)
    if no_hours:
        print("영업시간 못 읽음:", no_hours)
    if no_tel:
        print("전화번호가 없거나 번호로 읽히지 않아 뺀 매장 %d건" % len(no_tel))
    if already_written:
        print("앞선 파일이 이미 담아 건너뜀 %d건 (식당·시설이 브랜드 목록에도 있어 정상)"
              % len(already_written))
    if blocked_by_hand:
        # 줄표(—)를 쓰지 않는다. 윈도우 콘솔 기본 코드페이지(cp949)에 없어
        # 리다이렉트하면 여기서 UnicodeEncodeError로 죽는다.
        print("수작업 오버레이가 있어 자동 생성을 비움 %d건. 전화번호는 사람이 넣어야 한다:"
              % len(blocked_by_hand))
        print("  " + ", ".join(sorted(set(blocked_by_hand))))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("확인일(YYYY-MM-DD)을 인자로 준다 — 오늘 날짜를 코드가 멋대로 정하지 않는다.")
    main(sys.argv[1])
