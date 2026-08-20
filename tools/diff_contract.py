"""같은 요청을 파이썬 백엔드와 스프링 백엔드에 보내 응답을 비교한다.

    python tools/diff_contract.py https://navigation-api-....run.app http://localhost:8080

계약이 어긋났는지 보는 도구다. 이식이 끝났다는 근거를 "테스트가 통과한다"가 아니라
"두 서버가 같은 답을 낸다"로 만든다.

세 가지는 달라도 통과시킨다.

  1. 부동소수 끝자리 — 좌표 변환의 연산 순서가 numpy와 자바에서 달라 13~14번째 자리가
     흔들린다. 1e-9 상대오차 안이면 같다고 본다(위경도 1e-9도 ≈ 0.1mm).
  2. 불투명 해시 — tile_revision·graph revision·ETag는 blake2b와 SHA-256으로 알고리즘이
     다르다(대응표 참고). 값이 아니라 "있는지"만 본다.
  3. MVT 바이트 — 인코더가 다르니 바이트가 같을 수 없다. 크기 차이가 1% 안이면 같다고 본다.

HTTP는 curl로 부른다. urllib을 쓰면 msys 파이썬에 CA 번들이 없어 SSL 검증에서 막힌다.
"""

from __future__ import annotations

import json
import subprocess
import sys

# 값이 아니라 존재만 확인할 키. 두 서버의 해시 알고리즘이 다르다.
OPAQUE_KEYS = {"tile_revision", "revision", "graph_revision"}

# 순서만 다를 때의 표시. 실패로 세지 않는다.
ORDER_ONLY = "순서만 다름 (내용은 같다)"

FLOAT_TOLERANCE = 1e-9
MVT_SIZE_TOLERANCE = 0.01

BUILDING = "thehyundai-seoul"
PATHS = [
    "/health",
    "/health/ready",
    "/buildings",
    f"/buildings/{BUILDING}",
    f"/buildings/{BUILDING}/categories",
    f"/buildings/{BUILDING}/stores",
    # 한글은 퍼센트 인코딩해서 보낸다. 날것으로 보내면 Tomcat이 RFC대로 400을 내고
    # Starlette는 받아 준다 - 서버 차이가 아니라 요청이 규격 밖인 것이다.
    f"/buildings/{BUILDING}/stores?q=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4",  # 스타벅스
    f"/buildings/{BUILDING}/store-index",
    f"/buildings/{BUILDING}/floors/1F",
    f"/buildings/{BUILDING}/floors/B4",
    f"/buildings/{BUILDING}/graph",
    f"/buildings/{BUILDING}/floors/1F/graph",
    f"/buildings/{BUILDING}/places/PO-b1f2u6pn19153",
    "/.well-known/assetlinks.json",
    "/.well-known/apple-app-site-association",
    # MVT — 건물을 덮는 타일이라야 의미가 있다. 비는 타일은 양쪽 다 빈 레이어를 낸다.
    f"/buildings/{BUILDING}/floors/1F/tiles/16/55874/25388.mvt",
    f"/buildings/{BUILDING}/floors/B4/tiles/16/55874/25388.mvt",
    "/fonts/Pretendard Regular/44032-44287.pbf",
]


# 자연어 질의 대조. (경로, 본문) 쌍이다. 임베딩이 필요한 열린 질의는 스프링이 아직
# 경량 경로만 가져서 갈리는 것이 정상이라, 여기 담는 것은 경량이 답하는 질의다.
QUERIES = [
    "스타벅스", "스타벅스 리저브", "화장실", "엘리베이터", "에스컬레이터",
    "신발", "밥집", "커피", "명품", "노스 페이스", "이솝", "MLB",
    "화장실 몇 층이야", "스타벅스 어디야", "주차", "출구", "물품 보관함",
]
POST_CASES = (
    [(f"/query/destination", {"text": q, "building_id": BUILDING}) for q in QUERIES]
    + [(f"/query/info", {"text": q, "building_id": BUILDING}) for q in QUERIES]
    + [(f"/query/ai", {"text": q, "building_id": BUILDING}) for q in QUERIES]
)


def fetch(base: str, path: str, body: dict | None = None) -> tuple[int, bytes]:
    command = ["curl", "-s", "-w", "\n%{http_code}", "-g", base + path]
    payload = None
    if body is not None:
        # 본문을 stdin으로 넘긴다. 파일이나 인자로 주면 콘솔 인코딩(CP949)을 타서 한글이 깨진다.
        command += ["-X", "POST", "-H", "Content-Type: application/json; charset=utf-8", "--data-binary", "@-"]
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")

    result = subprocess.run(command, input=payload, capture_output=True)
    received, _, code = result.stdout.rpartition(b"\n")
    return int(code or 0), received


# 두 값이 실질적으로 같은지. 다르면 사람이 읽을 설명을 쌓는다.
def compare(a: object, b: object, path: str, out: list[str]) -> None:
    if len(out) >= 10:
        return
    key = path.rsplit(".", 1)[-1].split("[")[0]
    if key in OPAQUE_KEYS:
        if bool(a) != bool(b):
            out.append(f"{path}: 한쪽만 비어 있다 ({a!r} vs {b!r})")
        return
    if isinstance(a, bool) or isinstance(b, bool):
        if a is not b:
            out.append(f"{path}: {a!r} vs {b!r}")
        return
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        scale = max(abs(a), abs(b), 1.0)
        if abs(a - b) / scale > FLOAT_TOLERANCE:
            out.append(f"{path}: {a!r} vs {b!r}")
        return
    if type(a) is not type(b):
        out.append(f"{path}: 타입 {type(a).__name__} vs {type(b).__name__}")
        return
    if isinstance(a, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a:
                out.append(f"{path}.{k}: 파이썬에 없다")
            elif k not in b:
                out.append(f"{path}.{k}: 스프링에 없다")
            else:
                compare(a[k], b[k], f"{path}.{k}", out)
        return
    if isinstance(a, list):
        if len(a) != len(b):
            out.append(f"{path}: 길이 {len(a)} vs {len(b)}")
        for i, (x, y) in enumerate(zip(a, b)):
            compare(x, y, f"{path}[{i}]", out)
        return
    if a != b:
        out.append(f"{path}: {a!r} vs {b!r}")


# 비교용 정규형. 부동소수는 유효숫자 9자리로 자르고, sort_lists면 리스트 순서를 지운다.
def canon(value: object, sort_lists: bool) -> object:
    if isinstance(value, bool) or value is None:
        return value
    if isinstance(value, float):
        return float(f"{value:.9g}")
    if isinstance(value, dict):
        return {k: canon(v, sort_lists) for k, v in sorted(value.items()) if k not in OPAQUE_KEYS}
    if isinstance(value, list):
        items = [canon(v, sort_lists) for v in value]
        return sorted(items, key=lambda x: json.dumps(x, sort_keys=True)) if sort_lists else items
    return value


def diff_path(py_base: str, sp_base: str, path: str, body: dict | None = None) -> list[str]:
    py_code, py_body = fetch(py_base, path, body)
    sp_code, sp_body = fetch(sp_base, path, body)

    if py_code != sp_code:
        return [f"상태코드 {py_code} vs {sp_code}"]

    # 바이너리(MVT·글리프)는 인코더가 달라 바이트가 같을 수 없다. 크기로 본다.
    if path.endswith((".mvt", ".pbf")):
        scale = max(len(py_body), len(sp_body), 1)
        gap = abs(len(py_body) - len(sp_body)) / scale
        if gap > MVT_SIZE_TOLERANCE:
            return [f"크기 {len(py_body)}B vs {len(sp_body)}B ({gap:.1%} 차이)"]
        return []

    try:
        py_json, sp_json = json.loads(py_body), json.loads(sp_body)
    except json.JSONDecodeError:
        return [] if py_body == sp_body else [f"본문이 다르다({len(py_body)}B vs {len(sp_body)}B)"]

    if canon(py_json, False) == canon(sp_json, False):
        return []

    # 내용은 같은데 순서만 다른 경우. PostgreSQL의 기본 콜레이션이 SQLite의 바이트
    # 순서와 달라 같은 `order by ... s.id`가 다른 줄 순서를 낸다. 컷오버 뒤에는
    # 스프링 하나만 서빙하므로 순서는 그 안에서만 결정적이면 된다.
    if canon(py_json, True) == canon(sp_json, True):
        return [ORDER_ONLY]

    out: list[str] = []
    compare(py_json, sp_json, "", out)
    return out


def selftest() -> None:
    out: list[str] = []
    compare(1.0, 1.0 + 1e-12, "x", out)
    assert out == [], out
    compare(1.0, 1.1, "x", out)
    assert out, "명백히 다른 값을 놓쳤다"
    out = []
    compare("blake2b값", "sha256값", "a.tile_revision", out)
    assert out == [], out
    compare("", "sha256값", "a.tile_revision", out)
    assert out, "한쪽만 빈 리비전을 놓쳤다"
    out = []
    compare({"a": 1}, {"b": 1}, "", out)
    assert len(out) == 2, out
    assert canon({"a": [2, 1], "tile_revision": "x"}, False) == {"a": [2, 1]}
    assert canon({"a": [2, 1]}, True) == {"a": [1, 2]}
    assert canon(1.000000000123, False) == canon(1.0, False)
    print("ok", file=sys.stderr)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        selftest()
        raise SystemExit(0)
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)

    py_base, sp_base = sys.argv[1].rstrip("/"), sys.argv[2].rstrip("/")
    failed = 0
    cases = [(path, None) for path in PATHS] + POST_CASES
    for path, body in cases:
        label = path if body is None else f'{path} "{body["text"]}"'
        diffs = diff_path(py_base, sp_base, path, body)
        order_only = diffs == [ORDER_ONLY]
        mark = "OK  " if not diffs else ("주의" if order_only else "다름")
        print(f"{mark} {label}")
        for line in diffs:
            print(f"       {line}")
        failed += bool(diffs) and not order_only

    print(f"\n{len(cases) - failed}/{len(cases)} 일치")
    raise SystemExit(1 if failed else 0)
