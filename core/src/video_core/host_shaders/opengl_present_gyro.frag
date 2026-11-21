// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
//? #version 430 core

layout(location = 0) in vec2 frag_tex_coord;
layout(location = 0) out vec4 color;

layout(binding = 0) uniform sampler2D color_texture;
layout(binding = 1) uniform sampler2D color_texture_r;

uniform vec4 resolution;
uniform int layer;
uniform vec4 gyro_params;

void main() {
    float depth_weight = clamp(gyro_params.z * gyro_params.w, 0.0, 2.0);
    vec2 parallax = vec2(gyro_params.x, gyro_params.y) * (0.012 * depth_weight);
    float eye_mix = clamp(0.5 + gyro_params.x * 0.5 * depth_weight, 0.0, 1.0);
    vec4 left_col = texture(color_texture, clamp(frag_tex_coord - parallax * 0.5, vec2(0.0), vec2(1.0)));
    vec4 right_col = texture(color_texture_r, clamp(frag_tex_coord + parallax * 0.5, vec2(0.0), vec2(1.0)));
    color = mix(left_col, right_col, eye_mix);
}
