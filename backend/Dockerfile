FROM maven:3.9.9-amazoncorretto-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21.0.12_8-jdk-alpine-3.23
WORKDIR /app
COPY --from=build /app/target/taps-backend-0.0.1-SNAPSHOT.jar app.jar
ENV HOME=/tmp JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1
EXPOSE 8080
USER 1001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
