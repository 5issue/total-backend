# 1단계: 빌드 스테이지 (JDK 25 + 내장 gradlew)
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
# 멀티모듈 전체 복사 (common 의존성 포함)
COPY . .
# gradlew 실행 권한 부여
RUN chmod +x gradlew
# product 서비스를 실행 가능한 jar로 빌드
RUN ./gradlew :product-service:bootJar --no-daemon

# 2단계: 실행 스테이지 (가벼운 JRE)
FROM eclipse-temurin:25-jre
WORKDIR /app
# 빌드된 jar만 가져오기
COPY --from=builder /app/services/product-service/build/libs/*.jar app.jar
# product 서비스 포트
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
