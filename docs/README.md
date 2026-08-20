# 구조 지도

## 화면 ↔ 패키지

클라이언트는 화면이 사실상 `map_shell` 하나고 그 위에 시트·패널이 뜬다. 그래서 "화면"을
파일이 아니라 **사용자가 보는 표면**으로 잡고, 그 단위로 패키지를 갈랐다.

| 화면에 보이는 것 | 엔드포인트 | 패키지 |
|---|---|---|
| 건물 정보 시트 | `GET /buildings`, `GET /buildings/{id}` | `building` |
| 도면(층 평면도) | `GET /buildings/{id}/floors/{floor}` | `floormap` |
| 도면 위 도형·라벨 | `GET .../floors/{floor}/tiles/{z}/{x}/{y}.mvt` | `tile` |
| 라벨 글자 | `GET /fonts/{stack}/{s}-{e}.pbf` | `glyph` |
| 카테고리 칩·시트 | `GET .../categories`, `.../stores`, `.../store-index` | `category` |
| 장소 상세 시트 | `GET .../places/{placeId}` | `place` |
| 경로 안내(선) | `GET .../graph`, `.../floors/{f}/graph` | `route` |
| 공유 링크 진입 | `/.well-known/*`, `/place/{b}/{p}` | `share` |
| (화면 없음) | `/health`, `/health/live`, `/health/ready` | `health` |
| **검색 패널** | `POST /query/destination·ai·info` | **미착수** (아래) |

**엔드포인트 17개 이식 완료.** 남은 3개는 `search`뿐이다.

## 패키지 안의 계층

```
<기능>/
├─ controller/   경로 파라미터, HTTP 상태 변환만
├─ service/      트랜잭션 경계, 규칙
├─ repository/   조회
├─ domain/       JPA 엔티티
└─ dto/          응답 레코드
```

**예외를 두지 않는다.** 컨트롤러 하나뿐인 기능(`glyph`, `share`)도 `controller/`를 만든다.
"작으면 평면으로" 규칙을 두면 매번 "이 기능은 나눌 만큼 큰가"를 판단해야 하고, 그 판단이
쌓이면 구조가 사람마다 달라진다. 근거는 [001](decisions/001-화면단위-패키지.md).

### 공용 코드는 `common/`

| | 무엇 |
|---|---|
| `common/geo/` | 좌표 변환(6-DOF affine), 공유 폴리곤 분할, 라벨 점 |
| `common/geometry/` | `LocalPoint`·`LatLng` — 엔티티와 DTO가 함께 쓰는 좌표 타입 |
| `common/web/` | Cache-Control·ETag 헬퍼 |
| `common/config/` | 캐시 수명 설정, p6spy 배선 |

**엔티티는 가장 강하게 연관된 기능에 두고 다른 기능이 import한다.** `Store`는 `place/domain/`에
있지만 `floormap`·`category`·`tile`이 함께 읽는다 — 엔티티는 화면이 아니라 스키마라, 화면 단위로
복제할 대상이 아니다.

## search가 미착수인 이유

`/query/*` 3개는 코드 이식이 되지 않는다. 나머지 17개는 기계적 번역이었다.

| 파이썬이 쓰는 것 | 자바 사정 |
|---|---|
| `kiwipiepy` (한국어 형태소) | 대응 라이브러리 없음 → Elasticsearch Nori |
| `faiss-cpu` (벡터 검색) | 공식 바인딩 없음 → Elasticsearch kNN, pgvector |
| `sentence-transformers` (임베딩) | ONNX/DJL로 가능하나 토크나이저 이식이 함정 |

ES를 붙일지, 파이썬 백엔드로 프록시할지, 더 미룰지는 아직 정하지 않았다. **정하지 않은 것을
ADR로 쓰지 않는다** — 정해지면 `decisions/`에 추가한다.

그때까지 앱의 검색 패널은 파이썬 백엔드를 봐야 한다.

## 결정 기록

| | |
|---|---|
| [001](decisions/001-화면단위-패키지.md) | 계층이 아니라 화면으로 패키지를 가른다 |
| [002](decisions/002-경로계산은-클라이언트에-남긴다.md) | 최단 경로를 서버로 가져오지 않는다 |
| [003](decisions/003-SQLite에서-PostgreSQL로.md) | PostgreSQL을 쓴다 |
| [004](decisions/004-좌표변환은-6DOF-affine.md) | 좌표 변환은 6-DOF affine이다 |
| [005](decisions/005-공유-폴리곤-분할.md) | 한 폴리곤을 둘이 나눠 쓰는 자리만 나눈다 |
| [006](decisions/006-상세-섹션-순서.md) | 상세 섹션 순서를 서버가 고정한다 |
| [007](decisions/007-MVT는-직접-인코딩한다.md) | MVT 바이트를 직접 인코딩한다 |
| [008](decisions/008-타일-리비전은-입력-해시.md) | 타일 리비전은 타일 입력의 해시다 |

## 이식하면서 깨진 것들

[migration/windows-boot4-함정.md](migration/windows-boot4-함정.md) — Hibernate 네이밍, 테스트 yml
shadowing, CP949 인코딩, p6spy가 조용히 죽는 두 가지 이유, Boot 4에서 옮겨간 패키지들.
같은 데서 두 번 넘어지지 않으려고 적어 둔다.
