FROM openjdk:17-jdk-slim

WORKDIR /app

COPY services/notification-service/build/libs/*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]
