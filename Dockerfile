#
# 변경 요청: "chat server를 도커로 띄울 수 있게 수정" 반영.
# 목적:
# - chat server를 이미지로 빌드해 단일 컨테이너(8080)로 실행
# - 실행 환경을 Docker 기반으로 고정해 재현성을 높임
#

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY . .

# 테스트는 별도 검증 단계에서 수행하고, 이미지 빌드 속도를 위해 제외한다.
# 주의: 프로젝트 gradle wrapper(9.3.0)를 사용해 버전 불일치를 방지한다.
RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test

# 변경 요청: "jvm 지표 수집 실패 원인(jcmd/jstat 부재) 해결" 반영.
# 이유:
# - monitoring 앱이 컨테이너 내부에서 jcmd/jstat를 실행해 Heap/GC 지표를 수집한다.
# - jre 이미지에는 해당 도구가 없어 jcmd not found가 발생하므로 런타임 이미지를 jdk로 전환한다.
FROM eclipse-temurin:21-jdk
WORKDIR /app

COPY --from=builder /build/build/libs/*.jar /app/app.jar

EXPOSE 8080

# 주의:
# - Redis Cluster 노드는 compose 서비스명(redis-node-*)으로 연결한다.
# - Redis Stream(6379)은 host.docker.internal로 연결한다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
