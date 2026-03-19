# CARML Docker image for executing RML mappings.
# Uses GraalVM JDK 17 for better JIT compilation performance.
#
# Build (from carml-jar/ directory):
#   mvn clean install -Pquick
#   docker build --build-arg JAR_FILE=carml-app/carml-app-rdf4j/target/carml-jar-rdf4j-*.jar -t carml .
#
# Usage:
#   docker run -v /path/to/data:/data carml map -m /data/mapping.ttl -F nt
#
# Evaluator selection:
#   -E auto          Best evaluator per view (default)
#   -E in-process-db   Force in-process database evaluator for all views
#   -E reactive      Force reactive evaluator for all views

FROM ghcr.io/graalvm/jdk-community:17

# Install minimal utilities
RUN microdnf install -y findutils && microdnf clean all

# Copy application JAR
ARG JAR_FILE
COPY ${JAR_FILE} /app/app.jar

# Arrow memory access requires --add-opens for Java 17+
ENV JAVA_TOOL_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED"

# Generate CDS archive for faster startup
RUN java -XX:ArchiveClassesAtExit=/app/carml.jsa -jar /app/app.jar map --help > /dev/null 2>&1 || true

WORKDIR /data

ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/carml.jsa", "-jar", "/app/app.jar"]
