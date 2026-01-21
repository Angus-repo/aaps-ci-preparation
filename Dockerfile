FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/aaps-ci-preparation-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]