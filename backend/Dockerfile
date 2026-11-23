FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY docs/source/brp_ejemplo.csv docs/source/brp_ejemplo.csv
COPY backend/gradlew backend/build.gradle backend/settings.gradle backend/
COPY backend/gradle/ backend/gradle/
COPY backend/src/main/ backend/src/main/
WORKDIR /workspace/backend
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
