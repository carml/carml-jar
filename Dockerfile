# CARML Docker image for executing RML mappings.
#
# Build (from carml-jar/ directory):
#   mvn clean install -Pquick
#   docker build --build-arg JAR_FILE=carml-app/carml-app-rdf4j/target/carml-jar-rdf4j-*.jar -t carml .
#
# Usage:
#   docker run -v /path/to/data:/data carml map -m /data/mapping.ttl -F nt
#
# Evaluator selection:
#   -E auto            Best evaluator per view (default)
#   -E in-process-db   Force in-process database evaluator for all views
#   -E reactive        Force reactive evaluator for all views
#
# Spill-to-disk (for larger-than-memory datasets):
#   The --spill-to-disk flag stores the in-process database file and temporary spill files
#   in /carml-spill inside the container. Mount a host directory or Docker volume there to
#   avoid filling the container's writable layer:
#
#   docker run -v /path/to/data:/data -v /tmp/carml-spill:/carml-spill \
#     carml map --spill-to-disk -m /data/mapping.ttl -F nt
#
#   The in-process DB automatically halves its memory budget and adjusts thread count to
#   fit within the container's memory limit. Minimum 512 MB container memory recommended.
#
# GC tuning:
#   The default GC is G1GC, which is optimal for the in-process-db evaluator (Arrow batch
#   transfers). For reactive-only workloads, ZGC may improve throughput under high
#   GC pressure. Override via JAVA_TOOL_OPTIONS:
#
#   docker run -e JAVA_TOOL_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED -XX:+UseZGC -XX:+ZGenerational" \
#     -v /path/to/data:/data carml map -E reactive -m /data/mapping.ttl -F nt

FROM ghcr.io/graalvm/jdk-community:21

# Install minimal utilities
RUN microdnf install -y findutils && microdnf clean all

# Copy application JAR
ARG JAR_FILE
COPY ${JAR_FILE} /app/app.jar

# Arrow memory access requires --add-opens for Java 17+
ENV JAVA_TOOL_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED"

# Spill-to-disk directory for the in-process database file and intermediate spill files.
# The in-process DB's temp_directory is set to this path when --spill-to-disk is used.
# Mount a host directory or Docker volume here for larger-than-memory datasets.
RUN mkdir -p /carml-spill

# Generate CDS archive for faster startup
RUN java -XX:ArchiveClassesAtExit=/app/carml.jsa -jar /app/app.jar map --help > /dev/null 2>&1 || true

# Run as non-root to avoid creating root-owned files in mounted volumes.
RUN mkdir -p /data && chown 1000:1000 /carml-spill /data
USER 1000

WORKDIR /data

ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/carml.jsa", "-jar", "/app/app.jar"]
