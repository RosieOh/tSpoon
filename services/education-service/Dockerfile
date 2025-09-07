FROM openjdk:17-jdk-slim

WORKDIR /app

COPY services/education-service/build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
