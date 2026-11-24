# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build stage
# Runs on the machine doing the build ($BUILDPLATFORM: arm64 on an Apple Silicon Mac).
# Java bytecode is the same on every CPU, so there's no need to emulate x86 here;
# only the final runtime image has to match GKE's nodes (linux/amd64).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the build files first and download dependencies in their own layer. Docker caches
# this layer, so changing a .java file doesn't re-download every dependency.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

COPY src src
# Tests run in CI, not inside the image build.
RUN ./mvnw -q -B -DskipTests package \
 && java -Djarmode=tools -jar target/returnmate-core-*.jar extract --layers --launcher --destination extracted

# ---------------------------------------------------------------- runtime stage
# JRE only: no compiler, no Maven, much smaller and less to attack.
FROM eclipse-temurin:21-jre

# Never run as root inside the container.
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app

# Spring Boot "layers": dependencies change rarely, our code changes often. Copying them
# separately means a code-only change pushes a few hundred KB, not the whole 60+ MB.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001
EXPOSE 8080

# Size the heap from the container's memory limit (not the node's), and exit on
# OutOfMemoryError so Kubernetes restarts the pod instead of leaving it half-broken.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
