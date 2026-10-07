# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Ontop 5.5.0 SPARQL endpoint (port 8080, path /sparql) over ONE site PLM database.
# Build from the repository root:
#   docker build -f modules/ontop/Dockerfile --build-arg PLM=fr .
# PLM selects modules/ontop/mappings/<plm>.r2rml.ttl; ONTOLOGY is the ontology path in the context.
# Runtime configuration (environment): JDBC_URL, DB_USER, DB_PASSWORD, or DB_SECRET_JSON
# (RDS secret: host, port, username, password) plus DB_NAME.
# Plain Dockerfile syntax only (no BuildKit extensions) so kaniko can build it.
# Official base images come from the ECR Public mirror (public.ecr.aws/docker/library), pinned to the multi-arch manifest-list
# digests published on 2026-10-02; refresh them with `crane digest public.ecr.aws/docker/library/<image>:<tag>`.
FROM ontop/ontop:5.5.0@sha256:d19a2055b02812c8ecc0a00cca1733c1669c4143dfeb728acbfdff92b45e94d7 AS ontop

# The Ontop image ships no CA certificates, so the JDBC driver is fetched and verified here.
FROM public.ecr.aws/docker/library/eclipse-temurin:11-jdk@sha256:8db2bcf62ae171d1247c331db0410d8bc6347e7493bdafe104bb9a33d59c1291 AS build
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
ARG PGJDBC_VERSION=42.7.13
ARG PGJDBC_SHA256=6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93
RUN mkdir -p /jdbc \
 && curl -sSfL -o /jdbc/postgresql-${PGJDBC_VERSION}.jar \
      https://repo1.maven.org/maven2/org/postgresql/postgresql/${PGJDBC_VERSION}/postgresql-${PGJDBC_VERSION}.jar \
 && echo "${PGJDBC_SHA256}  /jdbc/postgresql-${PGJDBC_VERSION}.jar" | sha256sum -c -
COPY --from=ontop /opt/ontop/lib/jackson-core-2.15.4.jar /opt/ontop/lib/jackson-databind-2.15.4.jar /opt/ontop/lib/jackson-annotations-2.15.4.jar /deps/
COPY modules/ontop/src/OntopDbProperties.java /build/
RUN javac --release 11 -cp '/deps/*:/jdbc/*' -d /build/classes /build/OntopDbProperties.java

FROM ontop/ontop:5.5.0@sha256:d19a2055b02812c8ecc0a00cca1733c1669c4143dfeb728acbfdff92b45e94d7
ARG PLM=fr
ARG ONTOLOGY=ontology/atelier.ttl
COPY --from=build /jdbc/ /opt/ontop/jdbc/
COPY --from=build /build/classes/ /opt/ontop/launcher/
COPY modules/ontop/src/entrypoint.sh /opt/ontop/atelier-entrypoint.sh
COPY modules/ontop/mappings/${PLM}.r2rml.ttl /opt/ontop/input/mapping.r2rml.ttl
COPY ${ONTOLOGY} /opt/ontop/input/ontology.ttl
# The OWL API XML catalog library (LGPL-3.0) is not used: the endpoint loads its mapping and ontology from
# the files above and configures no catalog, so the image ships without it.
USER root
RUN rm /opt/ontop/lib/org.protege.xmlcatalog-1.0.5.jar

# The unprivileged user of the Ontop image; stated here so the final stage carries it explicitly.
USER ontop
EXPOSE 8080
# Ontop answers on / once the mapping is loaded (the image ships wget, not curl). On ECS the task
# definition runs the same probe; this instruction serves local runs of the image.
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=3 \
  CMD ["sh", "-c", "wget -q -O /dev/null http://localhost:8080/ || exit 1"]
ENTRYPOINT ["/opt/ontop/atelier-entrypoint.sh"]
