FROM openjdk:17-jdk-slim

WORKDIR /app

COPY services/api-gateway/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
