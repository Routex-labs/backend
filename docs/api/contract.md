# 엔드포인트 계약

키 표기는 **snake_case**다. Flutter 클라이언트가 그렇게 파싱한다. 자바 필드는 camelCase로 쓰고
`spring.jackson.property-naming-strategy: SNAKE_CASE`가 변환한다. 이 규칙이 풀렸는지는 각 컨트롤러
테스트가 JSON 키를 직접 검사해서 잡는다.

**검증 기준의 단일 출처는 테스트다.** 이 문서는 계약을 사람이 읽기 위한 것이고, 값이 정말
그렇게 나가는지는 `src/test/java/spring/**/controller/*Test.java`가 고정한다.

## 건물 — `building`

### `GET /buildings`

```json
[{"id":"thehyundai-seoul","name":"더현대 서울","floors":["2F","1F","B1"],"default_floor":"1F"}]
```

| 필드 | 뜻 |
|---|---|
| `floors` | 엘리베이터 버튼판 순서(위층 → 아래층). **표시 순서일 뿐 기본 층이 아니다.** |
| `default_floor` | 앱이 처음 열 층. 지상 최저층(1F)이 기준이고, 지상층이 없으면 최상층. 층이 없으면 `null`. |

`floors[0]`을 초기 층으로 쓰면 안 된다. 지하층이 생기는 순간 앱이 최상층으로 열린다.

### `GET /buildings/{building_id}`

목록에 도면용 값을 더한다. 없는 건물이면 **404**.

| 필드 | 뜻 |
|---|---|
| `area_m2` · `perimeter_m` | 면적·둘레. 원천에 없으면 `null` |
| `footprint_local_m` | 건물 대표 외곽선(미터 좌표). **기준층 것이다** — 층별 외곽은 층 지도 응답을 쓴다 |
| `footprint_wgs84` | 위 외곽선을 6-DOF affine으로 옮긴 위경도. 외곽선이 비면 `null` |
| `tile_revision` | 타일 URL에 `?v=`로 붙일 버전 토큰 |

## 층 지도 — `floormap`

### `GET /buildings/{id}/floors/{floor}`

한 층을 그리는 전체 묶음. 없으면 **404**.

| 필드 | 뜻 |
|---|---|
| `navigation_coordinate_system` | `"local_m"` 고정 |
| `map_calibration_version` | 미보정이면 `"unversioned"` |
| `footprint_local_m` · `_wgs84` | 층 외곽선. 층 것이 없으면 건물 것으로 폴백 |
| `navigation_graph` | 층 그래프. 클라이언트가 캐시해 온디바이스 다익스트라에 쓴다 |
| `stores` | 매장 폴리곤. 화장실·엘리베이터도 매장으로 내려간다 |
| `pois` | 시설 마커 |

**못 걷는 면은 이 응답에 없다.** 화면은 MVT 타일의 `non_walkable` 레이어로 그린다 — 여기 넣으면
아무도 안 읽는데 B4는 기둥만 209개다.

매장의 `entrance_wgs84`는 원본에서 **다비오 공식 POI 핀 위치**다. 한 폴리곤에 매장이 여럿 붙은
자리에서 `centroid`는 전부 같은 값이라, 화면이 라벨을 흩을 때 쓸 수 있는 유일한 좌표다.

## 벡터 타일 — `tile`

### `GET /buildings/{id}/floors/{floor}/tiles/{z}/{x}/{y}.mvt[?v=]`

`application/vnd.mapbox-vector-tile`. 레이어 순서가 곧 클라이언트가 꽂는 순서다(뒤가 위).

```
footprint → stores → non_walkable → store_labels → pois
```

도형이 없는 층에서도 **빈 레이어를 낸다** — 층마다 레이어 유무가 갈리면 sourceLayer 배선이 그
층에서만 달라진다. 데이터가 없는 타일도 **200**이다(404는 MapLibre가 스타일 오류로 본다).

| | 캐시 |
|---|---|
| `?v=` 없음 | `max-age=60` |
| `?v=<tile_revision>` | `max-age=31536000, immutable` |

격자를 벗어난 z/x/y는 **400**, 없는 층은 **404**.

## 글리프 — `glyph`

### `GET /fonts/{fontstack}/{start}-{end}.pbf`

`fontstack`은 쉼표 목록이라 가진 폰트를 골라 쓴다. 커밋되지 않은 범위(한자 등)는 **404가 아니라
빈 200**이다 — MapLibre가 404를 스타일 오류로 보고 심볼 레이아웃을 멈추면 **같은 타일의 fill
레이어까지** 안 그려진다. 256자 단위가 아닌 범위는 **400**.

## 카테고리 — `category`

| 경로 | 응답 |
|---|---|
| `GET .../categories` | (층·대분류·소분류)별 매장 수. 대분류 없는 매장은 빠진다 |
| `GET .../stores?q=` | 이름 부분 일치. `q` 없으면 전체. **공유 폴리곤 분할을 걸지 않는다**(부분 집합이라 짝을 못 찾는다) |
| `GET .../store-index` | 자동완성 색인. **좌표를 싣지 않는다** |

셋 다 없는 건물이면 빈 배열이 아니라 **404**다.

`store-index`의 `kind`는 상세와 **같은 규칙**으로 판정한다(`store`/`facility`/`excluded`).
클라이언트가 소분류 목록을 들고 스스로 판정하면 그 목록이 서버와 갈라지는 날 조용히 어긋난다.

## 장소 상세 — `place`

### `GET /buildings/{id}/places/{placeId}`

`sections`가 비어 있어도 이 응답만으로 화면이 성립해야 한다 — 그것이 "정보 없음 카드"를 만들지
않기 위한 조건이다. 다른 건물 id로 조회하면 **404**다.

섹션 순서는 서버가 고정한다([006](../decisions/006-상세-섹션-순서.md)):

```
hero → notice → hours → tags → summary → menu → keyValue → demoInfo → links → businessInfo → map
```

- 값이 없으면 **섹션 자체를 만들지 않고**, 선택 키는 값이 있을 때만 싣는다.
- `kind`가 `excluded`면 섹션도 액션도 없다.
- 입구 노드가 없으면 `directions` 액션을 내리지 않는다.
- 영업시간은 **판정 문자열을 만들지 않는다.** 규칙만 내려보내고 지금 시각과의 비교는 화면이 한다.

## 헬스 — `health`

| 경로 | 응답 |
|---|---|
| `GET /health`, `/health/live` | `{"status":"ok"}`. 의존성을 보지 않는다 |
| `GET /health/ready` | `{"status":"ready","database":"ok","embedding_model":"unknown"}`. DB가 안 되면 **503** |

`embedding_model`은 `search` 이식 전이라 항상 `"unknown"`이고, 준비 여부를 막지 않는다.

## 공유 링크 — `share`

| 경로 | 응답 |
|---|---|
| `GET /.well-known/assetlinks.json` | Android App Links 검증 |
| `GET /.well-known/apple-app-site-association` | iOS Universal Links 검증(`application/json`이어야 읽는다) |
| `GET /place/{buildingId}/{placeId}` | 앱 미설치 fallback 페이지. **경로 파라미터를 이스케이프한다** |

fallback은 매장을 조회하지 않는다 — 조회를 붙이면 삭제된 매장에서 500이 나며 공유 링크가 통째로
죽는다.

## 아직 없는 것

`POST /query/destination`, `/query/ai`, `/query/info` — [../README.md](../README.md)의 search 절 참고.
