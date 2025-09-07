FROM openjdk:17-jdk-slim

WORKDIR /app

COPY services/eureka-server/build/libs/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
