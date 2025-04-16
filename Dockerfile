FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/aaps-ci-preparation-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]