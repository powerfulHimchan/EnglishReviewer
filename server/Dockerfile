FROM gradle:8.11.1-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY core ./core
COPY server ./server
# The Android application is compiled separately by CI; no SDK needed for the server image.
RUN sed -i 's/include(":app", ":core", ":server")/include(":core", ":server")/' settings.gradle.kts
RUN gradle :server:bootJar --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 reviewer
COPY --from=build /src/server/build/libs/server.jar /app/server.jar
USER reviewer
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
