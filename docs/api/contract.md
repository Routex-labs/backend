# 엔드포인트 계약

키 표기는 **snake_case**다. Flutter 클라이언트가 그렇게 파싱한다. 자바 필드는 camelCase로
쓰고 `spring.jackson.property-naming-strategy: SNAKE_CASE`가 변환한다. 이 규칙이 풀렸는지는
`BuildingControllerTest`가 JSON 키를 직접 검사해서 잡는다.

## GET /buildings

건물 목록. 외곽선 같은 무거운 값은 싣지 않는다.

```json
[
  {
    "id": "thehyundai-seoul",
    "name": "더현대 서울",
    "floors": ["2F", "1F", "B1"],
    "default_floor": "1F"
  }
]
```

| 필드 | 뜻 |
|---|---|
| `floors` | 엘리베이터 버튼판 순서(위층 → 아래층). **표시 순서일 뿐 기본 층이 아니다.** |
| `default_floor` | 앱이 처음 열 층. 지상 최저층(1F)이 기준이고, 지상층이 없으면 최상층. 층이 없으면 `null`. |

`floors[0]`을 초기 층으로 쓰면 안 된다. 지하층이 생기는 순간 앱이 최상층으로 열린다 —
파이썬 쪽에서 실제로 겪은 사고라 서버가 `default_floor`를 따로 내려준다.

## GET /buildings/{building_id}

건물 상세. 목록 응답에 도면용 값을 더한다. 없는 건물이면 **404**.

```json
{
  "id": "thehyundai-seoul",
  "name": "더현대 서울",
  "floors": ["2F", "1F", "B1"],
  "default_floor": "1F",
  "area_m2": 21000.0,
  "perimeter_m": 620.5,
  "footprint_local_m": [{"x": 0.0, "y": 0.0}, {"x": 100.0, "y": 0.0}],
  "footprint_wgs84": null,
  "tile_revision": null
}
```

| 필드 | 뜻 |
|---|---|
| `footprint_local_m` | 건물 대표 외곽선(미터 좌표). **기준층 것이다** — 층별 외곽은 층 지도 응답을 써야 한다. 층마다 윤곽이 다르다(지하 주차장이 지상보다 넓다). |
| `footprint_wgs84` | 야외 지도용 위경도 외곽선. 실측 앵커로 아핀을 피팅해야 나온다. **아직 항상 `null`** — `tile` 패키지와 함께 이식한다. |
| `tile_revision` | 타일 URL에 `?v=`로 붙일 버전 토큰. **아직 항상 `null`** — 없어도 동작은 같고 캐시 수명만 짧아진다. |

두 `null`은 계약 위반이 아니다. 파이썬 쪽 스키마도 둘 다 nullable이다.

## 아직 없는 엔드포인트

나머지 18개는 [../README.md](../README.md)의 표를 본다.
