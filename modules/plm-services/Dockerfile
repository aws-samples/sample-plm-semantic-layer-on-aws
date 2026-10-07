# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Builds one service image of modules/plm-services; PLM selects the module: fr, de, uk or es
# (a site PLM service, module plm-<PLM>) or core (the Atelier core service, module atelier-core).
# Build context is the repository root, so the shared ontology/policy.json can ship in the image:
#   finch build --build-arg PLM=fr -f modules/plm-services/Dockerfile .
# Plain Dockerfile syntax only (no BuildKit extensions) so kaniko can build it.
# Official base images come from the ECR Public mirror (public.ecr.aws/docker/library), pinned to the multi-arch manifest-list
# digests published on 2026-10-02; refresh them with `crane digest public.ecr.aws/docker/library/<image>:<tag>`.
ARG PLM=fr

# The build stage does not depend on PLM: the five images share it, so the modules compile once per change and each
# image takes its own jar. The dependencies resolve in a layer of their own, keyed on the poms.
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /src
COPY modules/plm-services/pom.xml ./
COPY modules/plm-services/plm-common/pom.xml plm-common/
COPY modules/plm-services/plm-fr/pom.xml plm-fr/
COPY modules/plm-services/plm-de/pom.xml plm-de/
COPY modules/plm-services/plm-uk/pom.xml plm-uk/
COPY modules/plm-services/plm-es/pom.xml plm-es/
COPY modules/plm-services/atelier-core/pom.xml atelier-core/
# A package run over the poms alone fetches exactly the plugins and dependencies the build below uses.
RUN mvn -B -q -DskipTests -Dspring-boot.repackage.skip=true package
COPY modules/plm-services/ ./
RUN mvn -B -q -DskipTests package \
 && mkdir /jars \
 && for site in fr de uk es; do cp "plm-$site/target/plm-$site.jar" "/jars/$site.jar"; done \
 && cp atelier-core/target/atelier-core.jar /jars/core.jar

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
RUN addgroup -S -g 10001 plm && adduser -S -u 10001 -G plm plm
WORKDIR /app
ARG PLM
COPY --from=build /jars/${PLM}.jar /app/app.jar
# The export-control policy the /tables endpoint enforces; read at startup from ATELIER_POLICY_FILE.
COPY ontology/policy.json /app/policy.json
# The released link store the core service's graph reset PUTs back (read from RELEASED_GRAPHS_DIR); inert in the PLM images.
COPY data/links.ttl data/fileindex.ttl data/labels.ttl /app/data/
USER 10001
EXPOSE 8080
# The service answers its health probe under its own code (/fr/health, /core/health). On ECS the task
# definition runs the same probe; this instruction serves local runs of the image.
ENV HEALTH_PATH=/${PLM}/health
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
  CMD ["sh", "-c", "wget -q -O /dev/null \"http://localhost:8080${HEALTH_PATH}\" || exit 1"]
# Sized for a 512 MB - 1 GB Fargate task: heap follows the container limit, leaving room for
# metaspace, threads and direct buffers; serial GC suits the fractional vCPU; exit on OOM so
# ECS replaces the task instead of leaving it degraded.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k -Djava.security.egd=file:/dev/urandom"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
