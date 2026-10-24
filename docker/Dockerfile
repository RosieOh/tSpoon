# 모든 Spring Boot 서비스가 공유하는 이미지 정의
#   빌드 인자 SERVICE: 모듈 이름 (예: user-service), PORT: 서버 포트
#
# 호스트에서 `./gradlew bootJar` 로 만든 jar 를 계층(layer)별로 풀어서 담는다.
# 의존성 레이어가 캐시되므로 코드만 바뀌면 application 레이어만 다시 만들어진다.

FROM eclipse-temurin:25-jre AS extractor
ARG SERVICE
WORKDIR /builder
COPY services/${SERVICE}/build/libs/${SERVICE}.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
ARG PORT
ENV SERVER_PORT=${PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
COPY --chmod=0755 docker/healthcheck.sh /usr/local/bin/healthcheck

WORKDIR /app
COPY --from=extractor /builder/extracted/dependencies/ ./
COPY --from=extractor /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor /builder/extracted/application/ ./

USER spring
EXPOSE ${PORT}
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=5 CMD ["healthcheck"]
ENTRYPOINT ["java", "-jar", "application.jar"]
