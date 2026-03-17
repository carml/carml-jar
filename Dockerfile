# CARML Docker image for KROWN benchmarking and general CLI usage.
# Uses GraalVM JDK 17 for better JIT compilation performance.
#
# Build (from carml-jar/ directory):
#   mvn clean install -Pquick
#   docker build --build-arg JAR_FILE=carml-app/carml-app-rdf4j/target/carml-jar-rdf4j-*.jar -t carml .
#
# KROWN usage (container stays alive, KROWN invokes commands via docker exec):
#   docker exec <container> java -jar /app/app.jar map -m /data/shared/mapping.ttl -F nt
#
# Direct usage:
#   docker run -v /path/to/data:/data carml java -jar /app/app.jar map -m /data/mapping.ttl -F nt
#
# Evaluator selection:
#   -E auto       Best evaluator per view (default)
#   -E duckdb     Force DuckDB for all views
#   -E reactive   Force reactive for all views

FROM ghcr.io/graalvm/jdk-community:17

# Install minimal utilities
RUN microdnf install -y findutils && microdnf clean all

# Copy application JAR
ARG JAR_FILE
COPY ${JAR_FILE} /app/app.jar

# KROWN shared data directory
RUN mkdir -p /data/shared
WORKDIR /data

# KROWN pattern: container stays alive, framework invokes commands via docker exec
CMD ["tail", "-f", "/dev/null"]
