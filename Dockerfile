# 빌드 단계. Gradle 래퍼가 자기 버전을 받아 쓰므로 JDK만 있으면 된다.
FROM eclipse-temurin:17-jdk AS build

WORKDIR /src

# 래퍼와 빌드 스크립트를 먼저 복사해 의존성 해석 레이어를 소스와 분리한다.
# 소스만 고친 재빌드가 의존성 다운로드를 다시 하지 않는다.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src

# 테스트는 굽지 않는다. 통합 테스트가 Testcontainers로 진짜 PostgreSQL을 띄우는데
# 빌드 컨테이너 안에는 도커 데몬이 없다. 테스트는 CI(ci.yml)가 책임진다.
RUN ./gradlew --no-daemon bootJar -x test

# 실행 단계. JDK(약 450MB) 대신 JRE만 담는다.
FROM eclipse-temurin:17-jre

WORKDIR /app

# 루트로 돌리지 않는다.
RUN useradd --create-home --shell /bin/bash appuser
COPY --from=build --chown=appuser:appuser /src/build/libs/*.jar app.jar
USER appuser

# Cloud Run은 $PORT로 요청을 보낸다(기본 8080). 스프링 기본값과 같지만 명시한다 —
# 서비스 설정에서 포트를 바꾸면 이 줄이 없을 때 조용히 안 뜬다.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar app.jar --server.port=${PORT:-8080}"]
