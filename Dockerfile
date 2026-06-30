# =====================================================================
# 멀티스테이지 빌드: Gradle(JDK21)로 bootJar 생성 → 경량 JRE 런타임
# =====================================================================

# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 캐시 최적화: 빌드 스크립트/래퍼 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon >/dev/null 2>&1 || true

# 소스 복사 후 실행 가능한 boot jar 생성 (테스트는 컨테이너 빌드에서 제외 — Testcontainers는 별도 실행)
COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# boot jar만 복사 (plain jar 제외)
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# 도커로 띄우면 별도 설정 없이 local 프로파일로 구동(컨테이너 DB_URL이 datasource를 덮어씀).
# compose의 environment로 override 가능.
ENV SPRING_PROFILES_ACTIVE=local

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
