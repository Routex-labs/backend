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

**남의 DB(Supabase 등)에 붙으려면** `.env.example`을 `.env`로 복사해 값을 채운다. `.env`는
커밋되지 않고, `application.yml`이 있으면 읽고 없으면 무시한다. 접속 정보를 `application.yml`에
적지 않는 이유가 이것이다 - 기본값이 남아 있으면 어느 쪽이 이기는지 매번 확인해야 한다.

```bash
cp .env.example .env
```

```bash
curl localhost:8080/buildings
curl localhost:8080/buildings/thehyundai-seoul
curl localhost:8080/buildings/thehyundai-seoul/floors/1F
curl -o tile.mvt localhost:8080/buildings/thehyundai-seoul/floors/1F/tiles/16/55883/25378.mvt
```

## 진행 상황

**엔드포인트 20개 전부 이식 완료.** 자연어 질의 3개(`POST /query/*`)도 들어왔다 — 다만 파이썬의
2단 구조 중 **1차 경량 경로만** 옮겼다. 실사용 질의("스타벅스"·"신발"·"화장실")는 전부 이 경로가
답하고, 임베딩이 실제로 필요한 것은 "생일선물 살만한 곳"처럼 이름·분류로 이어지지 않는 열린
질의뿐이다. 사정은 [docs/README.md](docs/README.md)에 있다.

## 실데이터로 띄우기

`data.sql`은 엔드포인트가 사는지 보는 최소 시드다(매장 9건). 도면이 제대로 그려지는지 보려면
파이썬 백엔드의 SQLite 실데이터(매장 1,640건)를 옮겨 넣는다.

```bash
# 1. 원본을 새로 시드한다(Navigation/backend에서). 낡은 navigation.db에는
#    non_walkable_polygons_local_m이 없어 못 걷는 면이 안 그려진다.
python -m scripts.seed.reset_and_seed

# 2. 스키마를 만든다. Hibernate가 만들 때까지 띄웠다가 내린다.
docker compose down -v          # 데모 시드가 남아 있으면 층 라벨이 충돌한다
./gradlew bootRun --args='--spring.sql.init.mode=never'

# 3. SQL로 뽑아 밀어 넣는다(psql이 없으면 도커로)
python tools/load_real_data.py ../Navigation/backend/data/navigation.db seed_real.sql
docker run --rm -i postgres:18 psql "<접속 URL>" -v ON_ERROR_STOP=1 -f - < seed_real.sql
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
