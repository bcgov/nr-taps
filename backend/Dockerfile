FROM maven:3.9.9-amazoncorretto-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21.0.9_10-jdk-alpine-3.23
WORKDIR /app
COPY --from=build /app/target/taps-backend-0.0.1-SNAPSHOT.jar app.jar
ENV HOME=/tmp JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
USER 1001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
