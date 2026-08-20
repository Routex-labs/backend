# 이식하면서 실제로 깨진 것들

건물 패키지 하나를 옮기는 동안 다섯 번 깨졌다. 전부 조용히 깨져서 로그만 봐서는
원인이 안 보이는 종류라 적어 둔다.

## 1. Hibernate는 끝에 붙은 대문자 앞에 언더바를 넣지 않는다

`perimeterM` → `perimeterm`, `footprintLocalM` → `footprint_localm`.
`areaM2`는 뒤에 숫자가 있어 `area_m2`로 정상 변환된다. 그래서 **셋 중 하나만 깨져서**
"네이밍 전략이 이상하다"는 생각까지 가는 데 시간이 걸렸다.

→ 해당 필드에 `@Column(name = ...)`을 명시한다.

## 2. `src/test/resources/application.yml`은 메인 것을 덮어쓴다

병합이 아니라 **shadowing**이다. 같은 클래스패스 경로라 하나만 읽힌다. 테스트에서
드라이버 하나 바꾸려고 만들었더니 시드 설정과 Jackson 네이밍 전략까지 통째로 사라져서
목록이 빈 배열로 나왔다.

→ 지웠다. Testcontainers `@ServiceConnection`이 접속 정보를 알아서 준다.

## 3. `data.sql`을 CP949로 읽어 한글이 깨진다

Windows 기본 코드페이지 때문이다. API가 200을 주고 구조도 맞아서 **테스트는 전부
통과했다** — 이름만 mojibake였다.

→ `spring.sql.init.encoding: UTF-8`. 그리고 통합 테스트가 `"더현대 서울"`을 직접
검사하도록 고쳤다. 인코딩 사고는 구조 검사로는 절대 안 잡힌다.

같은 이유로 `build.gradle`에 `options.encoding = 'UTF-8'`도 넣었다. 없으면 javac가
한글 소스를 CP949로 읽는다.

## 4. docker-compose 지원이 `spring.datasource.url`을 덮어쓴다

p6spy를 `jdbc:p6spy:postgresql://...` URL로 걸어 뒀는데 아무것도 안 찍혔다.
`spring-boot-docker-compose`가 실행 중인 컨테이너에서 `JdbcConnectionDetails`를 만들어
`spring.datasource.*`를 통째로 대체하기 때문이다. Testcontainers도 같은 방식이다.

→ URL이 아니라 `DataSource`를 감싼다(`P6SpyConfiguration`, 8줄). URL이 어디서 오든 걸린다.

## 5. `modulelist`를 적으면 로깅 모듈이 빠진다

`spy.properties`에 `modulelist=com.p6spy.engine.spy.P6SpyFactory`만 적었더니 감싸기만
하고 한 줄도 안 찍혔다. 기본값은 `P6SpyFactory` + **`P6LogFactory`** 둘이다.

→ `modulelist`를 아예 적지 않는다.

## Boot 4에서 옮겨간 것들

| 3.x | 4.1 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | 슬라이스별로 분리 (`-webmvc-test`, `-data-jpa-test`) |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |

start.spring.io의 `/metadata/client`는 버전 id를 `4.1.0.RELEASE`로 주지만 실제 BOM은
`4.1.0`이다. `.RELEASE`를 붙여 요청하면 500이 돌아온다.
