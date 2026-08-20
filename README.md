# backend

[Routex-labs/Navigation](https://github.com/Routex-labs/Navigation)의 FastAPI 백엔드를 Spring으로 옮기는 저장소.

클라이언트(Flutter)는 건드리지 않는다. **JSON 계약을 그대로 유지**하는 것이 이 이식의
유일한 성공 조건이다 — 앱이 바뀐 걸 눈치채지 못해야 한다.

## 실행

```bash
./gradlew bootRun
```

PostgreSQL은 따로 띄우지 않아도 된다. `spring-boot-docker-compose`가 `compose.yaml`을
자동으로 올리고 접속 정보를 주입한다. Docker Desktop만 켜져 있으면 된다.

```bash
curl localhost:8080/buildings
curl localhost:8080/buildings/thehyundai-seoul
curl localhost:8080/buildings/thehyundai-seoul/floors/1F
curl -o tile.mvt localhost:8080/buildings/thehyundai-seoul/floors/1F/tiles/16/55883/25378.mvt
```

## 진행 상황

**엔드포인트 20개 중 17개 이식 완료.** 남은 셋은 자연어 질의(`POST /query/*`)뿐인데, 이건 코드
이식으로 되지 않는다 — 한국어 형태소·벡터 검색·임베딩이 전부 파이썬 라이브러리에 묶여 있다.
자세한 사정과 선택지는 [docs/README.md](docs/README.md)에 있다.

## 실데이터로 띄우기

`data.sql`은 엔드포인트가 사는지 보는 최소 시드다(매장 9건). 도면이 제대로 그려지는지 보려면
파이썬 백엔드의 SQLite 실데이터(매장 1,640건)를 옮겨 넣는다.

```bash
docker compose down -v          # 데모 시드가 남아 있으면 층 라벨이 충돌한다
./gradlew bootRun --args='--spring.sql.init.mode=never'
# 다른 창에서: SQLite -> INSERT 문 -> psql
```

**`--spring.sql.init.mode=never`가 필수다.** 데모 시드는 `ON CONFLICT (id) DO NOTHING`이라
id 충돌은 넘어가지만, 층은 `(building_id, name)`이 유니크라 실데이터의 `1F`와 데모의 `ths-1f`가
같은 라벨로 부딪혀 기동이 실패한다.

## 테스트

```bash
./gradlew test
```

통합 테스트는 Testcontainers로 진짜 PostgreSQL 18을 띄운다. H2로 통과하고 PG에서
깨지는 상황을 만들지 않으려는 것이다.

## 스택

| | |
|---|---|
| Spring Boot | 4.1.0 |
| Java | 17 |
| DB | PostgreSQL 18 (드라이버 42.7.11, Boot BOM 관리) |
| SQL 로깅 | p6spy 3.9.1 |

버전을 `build.gradle`에 적은 것은 p6spy뿐이다. 나머지는 Boot BOM이 정한다.

## 문서

- [docs/README.md](docs/README.md) — 화면 ↔ 패키지 대응표, 진행 상황
- [docs/api/contract.md](docs/api/contract.md) — 엔드포인트 계약
- [docs/migration/대응표.md](docs/migration/대응표.md) — FastAPI 파일 ↔ Spring 클래스
- [docs/decisions/](docs/decisions/) — 왜 이 설계인가, 버린 대안
- [docs/migration/windows-boot4-함정.md](docs/migration/windows-boot4-함정.md) — 이식하면서 실제로 깨진 것들
