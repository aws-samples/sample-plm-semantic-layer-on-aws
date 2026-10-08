# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Atelier agent: Strands + ag-ui-strands behind FastAPI, AG-UI over SSE on port 8080.
# Build context: the repository root, like every image of this repo. Plain Dockerfile syntax (kaniko-compatible); the base image is
# multi-arch and every dependency ships aarch64 wheels, so the same file builds for arm64 or
# amd64 according to the builder's platform (kaniko: --custom-platform=linux/arm64).
# Official base images come from the ECR Public mirror (public.ecr.aws/docker/library), pinned to the multi-arch manifest-list
# digests published on 2026-10-02; refresh them with `crane digest public.ecr.aws/docker/library/<image>:<tag>`.
FROM public.ecr.aws/docker/library/python:3.12-slim@sha256:dddfd7e07f9d15aeeca61529320492139d21cac7f0070c00609243e51e4e0016

WORKDIR /app

COPY modules/agent/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY modules/agent/server.py .
COPY modules/agent/atelier_agent/ ./atelier_agent/

RUN useradd --create-home --uid 10001 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/agent/health', timeout=3)"]

CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8080"]
