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
| 검색 패널 | `POST /query/destination·ai·info` | `search` |

**엔드포인트 20개 전부 이식 완료.** `search`는 1차 경량 경로만 옮겼다 — 아래 참고.

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

## search에서 옮긴 것과 남긴 것

파이썬의 질의는 **2단**이다. 1차는 이름·카테고리·동의어·intent를 어휘로 맞추고(`source: light`),
1차가 놓쳤거나 모호한 열린 질의만 2차 임베딩 검색으로 넘긴다(`source: semantic`).

**1차만 옮겼다.** 배포된 파이썬에 직접 물어 확인한 분포가 근거다.

| 질의 | 파이썬이 답한 경로 |
|---|---|
| `스타벅스` → 스타벅스 리저브(B2) | `light` (0.14초) |
| `신발` · `밥집` → clarify | `light` |
| `생일선물 살만한 곳` | `semantic` |
| `따뜻한 겨울옷` | `semantic` |

즉 **"스타벅스"가 실내 검색으로 뜨는 것은 임베딩 기능이 아니다.** 이름 부분 일치라 1차가 푼다.

### 형태소는 Lucene Nori다

이 문서는 한때 "kiwipiepy 대응 라이브러리 없음 → Elasticsearch Nori"라고 적고 있었다. **틀린
서술이었다.**

- **Nori는 Elasticsearch 기능이 아니라 Lucene 본체의 분석기다.** ES는 그 위에 얹은 서버 껍데기라,
  우리는 `org.apache.lucene:lucene-analysis-nori` 의존성 한 줄로 서버 없이 쓴다.
- mecab-ko-dic 기반이라 **품사 태그 체계가 Kiwi와 같다.** 파이썬의 제거 규칙(J·E·V·MM·MA·NP·
  XSV·XSA)이 문자 그대로 옮겨졌다.
- 한국어 형태소 분석기는 원래 자바가 본진이다 — KOMORAN·Kkma·한나눔·Open Korean Text가 모두
  자바 라이브러리이고, KoNLPy가 파이썬에서 그것들을 감싼 것이다.

Nori 사용자 사전은 공백을 분절 구분자로 읽어 "물품 보관함" 같은 이름을 한 단어로 등록할 수 없다.
파이썬도 같은 한계가 있어 결과 쪽에서 되돌리는 보정을 갖고 있고(`_restore_truncated_name`),
`QueryMorph`가 그것을 그대로 옮겼다.

### 2차(임베딩)를 옮길 때

FAISS도 Elasticsearch도 필요 없다. **매장 1,640건 × 768차원 = 5MB**라 인메모리 코사인 전수 계산이면
끝이고, 인덱스 자료구조 자체가 과하다. 남는 건 임베딩 모델(ko-sroberta)을 ONNX Runtime이나 DJL로
올리는 일뿐인데, 그 순간 Cloud Run 메모리 요구가 1GiB에서 2GiB로 돌아간다 — 파이썬 배포가
`/query/ai` 한 건에 69.5초를 쓴 것도 이 모델 로드였다. 그래서 필요해질 때 붙인다.

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
| [009](decisions/009-intent는-어휘-매칭-1단에-태운다.md) | intent는 어휘 매칭 1단에 태운다 |

## 이식하면서 깨진 것들

[migration/windows-boot4-함정.md](migration/windows-boot4-함정.md) — Hibernate 네이밍, 테스트 yml
shadowing, CP949 인코딩, p6spy가 조용히 죽는 두 가지 이유, Boot 4에서 옮겨간 패키지들.
같은 데서 두 번 넘어지지 않으려고 적어 둔다.
