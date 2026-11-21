// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
#version 450 core
#extension GL_ARB_separate_shader_objects : enable

layout (location = 0) in vec2 frag_tex_coord;
layout (location = 0) out vec4 color;

layout (push_constant, std140) uniform DrawInfo {
    mat4 modelview_matrix;
    vec4 i_resolution;
    vec4 o_resolution;
    int screen_id_l;
    int screen_id_r;
    int layer;
    int reverse_interlaced;
    vec4 gyro_params; // x=tiltX (-1..1), y=tiltY (-1..1), z=strength, w=factor_3d (0..1)
};

layout (set = 0, binding = 0) uniform sampler2D screen_textures[3];

vec4 SampleEye(int screen_id, vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(1.0));
#ifdef ARRAY_DYNAMIC_INDEX
    return texture(screen_textures[screen_id], clamped);
#else
    switch (screen_id) {
    case 0:
        return texture(screen_textures[0], clamped);
    case 1:
        return texture(screen_textures[1], clamped);
    default:
        return texture(screen_textures[2], clamped);
    }
#endif
}

void main() {
    float depth_weight = clamp(gyro_params.z * gyro_params.w, 0.0, 2.0);
    // Subtle multi-plane holographic parallax between real 3DS left & right eye framebuffers
    vec2 parallax = vec2(gyro_params.x, gyro_params.y) * (0.012 * depth_weight);
    float eye_mix = clamp(0.5 + gyro_params.x * 0.5 * depth_weight, 0.0, 1.0);
    vec4 left_col = SampleEye(screen_id_l, frag_tex_coord - parallax * 0.5);
    vec4 right_col = SampleEye(screen_id_r, frag_tex_coord + parallax * 0.5);
    color = mix(left_col, right_col, eye_mix);
}
