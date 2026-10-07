// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The part's back faces pushed outwards by a fixed number of screen pixels, along the vertex normal in clip space.
uniform vec2 resolution;
uniform float width;
void main() {
  vec4 local = vec4(position, 1.0);
  vec3 n = normal;
  #ifdef USE_INSTANCING
    local = instanceMatrix * local;
    n = mat3(instanceMatrix) * n;
  #endif
  vec4 clip = projectionMatrix * modelViewMatrix * local;
  vec2 dir = (projectionMatrix * vec4(normalize(normalMatrix * n), 0.0)).xy;
  float len = length(dir);
  if (len > 0.0) clip.xy += dir / len * width * 2.0 / resolution * clip.w;
  gl_Position = clip;
}
