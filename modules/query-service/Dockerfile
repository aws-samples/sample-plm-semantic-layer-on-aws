# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Query service image. Build context is the repository root:
#   finch build -f modules/query-service/Dockerfile -t atelier-query-service .
# Plain Dockerfile syntax only (no BuildKit extensions) so kaniko can build it.
# Official base images come from the ECR Public mirror (public.ecr.aws/docker/library), pinned to the multi-arch manifest-list
# digests published on 2026-10-02; refresh them with `crane digest public.ecr.aws/docker/library/<image>:<tag>`.
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /src
COPY modules/query-service/pom.xml modules/query-service/
RUN mvn -B -q -f modules/query-service/pom.xml dependency:go-offline
# ontology/ (ontology, shapes, units) is packaged into the jar as classpath resources.
COPY ontology/ ontology/
COPY modules/query-service/src modules/query-service/src
RUN mvn -B -q -f modules/query-service/pom.xml -DskipTests package \
 && cp modules/query-service/target/query-service.jar /app.jar

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
RUN addgroup -S -g 10001 query && adduser -S -u 10001 -G query query
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# Inputs of the offline SPARQL-to-SQL reformulators: the R2RML each Ontop endpoint runs (the four
# PLMs and core), the ontology, and each source's serialised database metadata (see
# modules/ontop/metadata/README.md). A source whose files are absent reports no SQL in the evidence.
COPY modules/ontop/mappings/ /app/mappings/
COPY modules/ontop/metadata/ /app/metadata/
COPY ontology/atelier.ttl /app/ontology/atelier.ttl
# The export-control policy, shipped unchanged.
COPY ontology/policy.json /app/policy.json
ENV ATELIER_POLICY_FILE=/app/policy.json
# The released links and file-index graphs: what the change feed diffs the live graphs against. Restoring
# them, and the labels graph, is the core service's, from the copy in its own image.
COPY data/links.ttl data/fileindex.ttl /app/data/
ENV RELEASED_GRAPHS_DIR=/app/data
USER 10001
EXPOSE 8080
# On ECS the task definition runs the same probe; this instruction serves local runs of the image.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
  CMD ["sh", "-c", "wget -q -O /dev/null http://localhost:8080/query/health || exit 1"]
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
