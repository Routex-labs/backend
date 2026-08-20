# 003. SQLite 대신 PostgreSQL을 쓴다

## 결정

PostgreSQL 18. 로컬은 `compose.yaml`, 테스트는 Testcontainers.

## 왜

**JSON 컬럼이 결정적이다.** 외곽선·비보행 폴리곤·매장 상세 섹션이 전부 JSON으로
들어 있는데, PostgreSQL의 `jsonb`는 이걸 인덱싱하고 질의할 수 있다. SQLite의 JSON은
텍스트다. 나중에 `place` 패키지(DTO 26개, 대부분 JSON 섹션)를 이식할 때 차이가 난다.

**나중에 벡터 검색으로 이어진다.** `search` 패키지를 붙일 때 pgvector가 선택지에 남는다.
SQLite에서는 그 길이 없다.

## 버린 안

**SQLite 유지** — 원본과 같은 DB라 데이터 비교가 쉽다는 장점이 있었다. 하지만 JVM에서
SQLite는 실무 조합이 아니고, 학습 목적에도 어긋난다.

**H2 인메모리** — 설정이 0이고 `./gradlew bootRun` 한 줄로 뜬다. 방언이 달라 "H2에서는
통하는데 PG에서 안 되는" 버그를 나중에 몰아서 만나게 된다. Testcontainers가 그 값을
치르지 않고도 진짜 PG를 쓰게 해준다.

## 대가

Docker Desktop이 실행 전제조건이 됐다. `spring-boot-docker-compose`가 컨테이너를 자동으로
띄워서 명령은 여전히 `./gradlew bootRun` 한 줄이다.
