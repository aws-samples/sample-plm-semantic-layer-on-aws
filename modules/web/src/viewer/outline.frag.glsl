// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// A flat colour: the coloured line or the white band.
uniform vec3 colour;
void main() {
  gl_FragColor = vec4(colour, 1.0);
  #include <colorspace_fragment>
}
