# CARML Docker image for KROWN benchmarking.
# This Dockerfile is intended to be copied into KROWN's execution-framework/dockers/CARML/ directory.
#
# The pre-built carml:latest image should be used instead of building from this Dockerfile.
# See carml-jar/krown/README.md for build instructions.

FROM carml:latest

# KROWN shared data directory
RUN mkdir -p /data/shared

# KROWN pattern: container stays alive, framework invokes commands via docker exec
ENTRYPOINT []
CMD ["tail", "-f", "/dev/null"]
