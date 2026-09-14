# 1단계: 빌드용 부엌 (JDK 25 + 프로젝트 내장 gradlew로 빌드)
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
# 백엔드 소스 전체 복사 (멀티모듈이라 common 등 필요)
COPY . .
# gradlew 실행 권한 부여
RUN chmod +x gradlew
# auth 서비스를 실행 가능한 jar로 굽기 (내장 gradlew 사용)
RUN ./gradlew :auth-service:bootJar --no-daemon

# 2단계: 실행용 상 (jar만 담은 가벼운 이미지)
FROM eclipse-temurin:25-jre
WORKDIR /app
# 1단계에서 구운 jar만 가져오기
COPY --from=builder /app/services/auth-service/build/libs/*.jar app.jar
# auth 포트
EXPOSE 8081
# 컨테이너 뜨면 앱 실행
ENTRYPOINT ["java", "-jar", "app.jar"]