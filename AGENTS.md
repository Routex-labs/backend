# AGENTS.md

[Navigation](https://github.com/Routex-labs/Navigation)의 FastAPI 백엔드를 Spring으로 옮긴
저장소. 실행법·시드·스택은 [README.md](README.md)에 있다.

## 깨면 안 되는 것

**JSON 계약.** Flutter 앱은 건드리지 않는다. 응답의 필드 이름·타입·구조를 바꾸는 것은
계약 변경이다 — [docs/api/contract.md](docs/api/contract.md)를 먼저 보고, 바꿔야 한다면
문서를 같은 PR에서 함께 고친다.

## 구조

`src/main/java/spring/<도메인>/{controller,service,repository,domain,dto}`.
도메인은 building·place·route·search·tile·floormap·category·glyph·share·health,
공용 코드는 `common/`. 새 기능은 기존 도메인에 넣고, 없을 때만 도메인을 만든다.

## 확인

```bash
./gradlew test
```

통합 테스트는 Testcontainers로 진짜 PostgreSQL 18을 띄운다(Docker Desktop 필요).
H2로 바꾸지 않는다 — H2에서 통과하고 PG에서 깨지는 걸 막으려고 이렇게 두었다.

## 글

- 커밋·PR·문서·주석은 한글, 사용자와의 대화는 존댓말.
- 커밋과 PR 형식은 [.github/CONTRIBUTING.md](.github/CONTRIBUTING.md).
- 접속 정보는 `.env`에만 둔다. `application.yml`에 기본값을 적지 않는다.
