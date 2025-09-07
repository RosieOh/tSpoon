FROM openjdk:17-jdk-slim

WORKDIR /app

COPY services/qna-service/build/libs/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
