FROM maven:3.9.9-amazoncorretto-21-alpine AS build
WORKDIR /workspace/app

COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM eclipse-temurin:21.0.12_8-jdk-alpine-3.23

# Check the runtime JDK's time zone rules; the build stage and CI use other JDKs.
COPY checks/VerifyTimezone.java /tmp/VerifyTimezone.java
RUN java /tmp/VerifyTimezone.java && rm /tmp/VerifyTimezone.java

ENV HOME=/tmp \
    XDG_CACHE_HOME=/tmp/.cache

USER 1001
VOLUME /tmp
ARG DEPENDENCY=/workspace/app/target/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=60.0"

# The JVM zone comes from the deployment's TZ; don't set -Duser.timezone here.
ENTRYPOINT ["java","-Duser.name=TAPS-API","-Duser.home=/tmp","-Djava.io.tmpdir=/tmp","-XX:+UseParallelGC","-XX:MinHeapFreeRatio=20","-XX:MaxHeapFreeRatio=40","-XX:GCTimeRatio=4","-XX:AdaptiveSizePolicyWeight=90","-XX:ParallelGCThreads=2","-Djava.util.concurrent.ForkJoinPool.common.parallelism=8","-XX:CICompilerCount=2","-XX:+ExitOnOutOfMemoryError","-Djava.security.egd=file:/dev/./urandom","-cp","/app:/app/lib/*","ca.bc.gov.nrs.taps.TapsApplication"]
