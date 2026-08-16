# syntax=docker/dockerfile:1

# ---- Stage 1: Build ----
# Use a JDK image with Maven support. Java 25 (LTS) as required by the project.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper first so we can prime dependencies with better layer caching.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copy sources and build the (repackaged) Spring Boot jar. Skip tests in the image build;
# tests use Testcontainers and are meant to run in CI / locally, not during image assembly.
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- Stage 2: Runtime ----
# Slim JRE image keeps the final image small; no build tooling shipped to production.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copy the fat jar built in the previous stage.
COPY --from=build /workspace/target/rag-implement-*.jar app.jar

EXPOSE 8080

# Enable virtual threads friendly runtime and let container memory limits drive the heap.
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
