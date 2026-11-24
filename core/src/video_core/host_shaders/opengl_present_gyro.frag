// Copyright 2026 STORM 3DS Project
// Licensed under GPLv3 or any later version
//? #version 430 core

layout(location = 0) in vec2 frag_tex_coord;
layout(location = 0) out vec4 color;

layout(binding = 0) uniform sampler2D color_texture;
layout(binding = 1) uniform sampler2D color_texture_r;

uniform vec4 i_resolution;
uniform vec4 o_resolution;
uniform int layer;
uniform vec4 gyro_params;

float EstimatePointDepth(vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.001), vec2(0.999));
    vec3 cL = texture(color_texture, clamped).rgb;
    float luma = dot(cL, vec3(0.299, 0.587, 0.114));
    float maxC = max(cL.r, max(cL.g, cL.b));
    float minC = min(cL.r, min(cL.g, cL.b));
    float chroma = maxC - minC;
    float persp = clamp((1.0 - uv.x) * 0.54 + luma * 0.26 + chroma * 0.24, 0.0, 1.0);
    float perceptualDepth = mix(-0.48, 0.72, persp);

    vec3 cR0 = texture(color_texture_r, clamped).rgb;
    float sameDiff = dot(abs(cL - cR0), vec3(0.3333));
    if (sameDiff <= 0.025) {
        return perceptualDepth;
    }

    float stepY = max(2.0 * i_resolution.w, 1.0 / 200.0);
    float bestDiff = sameDiff;
    float bestK = 0.0;
    float shifts[6];
    shifts[0] = -4.0; shifts[1] = -2.0; shifts[2] = -1.0;
    shifts[3] = 1.0; shifts[4] = 2.0; shifts[5] = 4.0;
    for (int i = 0; i < 6; ++i) {
        float k = shifts[i];
        vec3 cRk = texture(color_texture_r, clamp(uv + vec2(0.0, k * stepY), vec2(0.001), vec2(0.999))).rgb;
        float d = dot(abs(cL - cRk), vec3(0.3333));
        if (d < bestDiff) {
            bestDiff = d;
            bestK = k;
        }
    }

    float conf = clamp((sameDiff - bestDiff) * 5.5, 0.0, 1.0);
    float stereoDepth = clamp((-bestK * 0.25) * 0.85, -0.80, 0.85);
    return mix(perceptualDepth, stereoDepth, conf * 0.75);
}

float EstimateSceneDepth(vec2 uv) {
    float dc = EstimatePointDepth(uv);
    vec2 stepUv = vec2(max(2.0 * i_resolution.z, 1.0 / 120.0), max(2.0 * i_resolution.w, 1.0 / 200.0));
    float dl = EstimatePointDepth(uv - vec2(0.0, stepUv.y));
    float dr = EstimatePointDepth(uv + vec2(0.0, stepUv.y));
    float du = EstimatePointDepth(uv + vec2(stepUv.x, 0.0));
    float dd = EstimatePointDepth(uv - vec2(stepUv.x, 0.0));
    return dc * 0.48 + (dl + dr + du + dd) * 0.13;
}

void main() {
    if (gyro_params.z <= 0.0001 || gyro_params.w <= 0.0001) {
        color = texture(color_texture, clamp(frag_tex_coord, vec2(0.001), vec2(0.999)));
        return;
    }

    float strengthNorm = clamp(gyro_params.z * gyro_params.w, 0.0, 1.5);
    vec2 parallaxVec = vec2(-gyro_params.y, gyro_params.x) * (0.008 + 0.024 * strengthNorm);

    float d1 = EstimateSceneDepth(frag_tex_coord);
    vec2 uv1 = clamp(frag_tex_coord + parallaxVec * d1, vec2(0.001), vec2(0.999));
    float d2 = EstimateSceneDepth(uv1);
    float depthFactor = mix(d1, d2, 0.45);
    vec2 baseUv = clamp(frag_tex_coord + parallaxVec * depthFactor, vec2(0.001), vec2(0.999));

    vec4 finalColor = texture(color_texture, baseUv);
    vec2 centered = (frag_tex_coord - vec2(0.5)) * 2.0;
    float s = min(strengthNorm, 1.0);
    float vig = 1.0 - dot(centered, centered) * (0.042 * s);
    float fgBoost = 1.0 + max(depthFactor, 0.0) * (0.065 * s);
    float sheen = clamp(
        (gyro_params.x * (frag_tex_coord.y - 0.5) + gyro_params.y * (frag_tex_coord.x - 0.5)) * (0.10 * s),
        -0.05,
        0.05
    ) * max(depthFactor, 0.0);

    color = vec4(clamp(finalColor.rgb * vig * fgBoost + vec3(sheen), 0.0, 1.0), finalColor.a);
}
