FROM openjdk:17-jdk-slim

WORKDIR /app

COPY services/event-service/build/libs/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
