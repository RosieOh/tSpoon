FROM openjdk:17-jdk-slim

WORKDIR /app

COPY services/community-service/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
