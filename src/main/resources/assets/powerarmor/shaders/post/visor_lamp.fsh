#version 330
#extension GL_ARB_separate_shader_objects : require

// The visor's lamp (client/VisorVision): brightens what's near the middle of the view and close by, as if a lamp on the
// helmet shone where the pilot looks. Run over the finished picture (a post effect), so it can only brighten what's
// already there: a block lit by nothing is still the dim floor the lightmap leaves it (the overworld's ambient light),
// and that's what gets multiplied up.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
// 2x1 knobs written by VisorVision: pixel 0 = strength, tan(half the vertical fov) / 2, range / 64; pixel 1 = colour
uniform sampler2D LampSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
    vec2 LampSize;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// the camera's near plane (Camera.PROJECTION_Z_NEAR)
const float NEAR = 0.05;
// how much a dark pixel at the cone's centre, close by, is multiplied up: about 1 / the overworld's darkest (its ambient
// light after the brightness setting, ~0.09), so full lamp light shows a block about as bright as daylight would
const float GAIN = 10.0;

void main() {
    vec4 colour = texture(InSampler, texCoord);
    vec4 knobs = texelFetch(LampSampler, ivec2(0, 0), 0);
    vec3 tint = texelFetch(LampSampler, ivec2(1, 0), 0).rgb;
    float strength = knobs.r;
    float tanHalf = knobs.g * 2.0;
    float range = knobs.b * 64.0;

    // Minecraft's depth is reversed (1 at the near plane, 0 = nothing drawn: the sky), so the distance along the view
    // is about NEAR / depth (the far plane is far enough away to ignore).
    float depth = texture(DepthSampler, texCoord).r;
    if (depth <= 0.0 || strength <= 0.0) {
        fragColor = colour;
        return;
    }
    float along = NEAR / depth;
    // how far off the middle of the view this pixel is, as tan(angle) (x scaled by the window's shape)
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec2 off = vec2(ndc.x * tanHalf * OutSize.x / OutSize.y, ndc.y * tanHalf);
    float tanOff = length(off);
    float cosOff = inversesqrt(1.0 + tanOff * tanOff);
    float dist = along / cosOff;

    // the beam: bright inside ~12 degrees, gone by ~30, and a faint spill out to ~50
    float beam = 0.8 * smoothstep(cos(radians(30.0)), cos(radians(12.0)), cosOff)
               + 0.2 * smoothstep(cos(radians(50.0)), cos(radians(20.0)), cosOff);
    // brightness falls off with distance (half at 12 blocks), fades out at the range; nothing within a block and a half
    // (the suit's own arms and tool in the cockpit view: the lamp is on the helmet, above them)
    float reach = 1.0 / (1.0 + (dist / 12.0) * (dist / 12.0));
    reach *= 1.0 - smoothstep(range * 0.6, range, dist);
    reach *= smoothstep(1.0, 1.6, dist);
    float light = strength * beam * reach;

    // dark pixels gain the most, bright ones (already lit by a torch or the sun) hardly change: no blown-out whites
    float lum = dot(colour.rgb, vec3(0.299, 0.587, 0.114));
    float headroom = (1.0 - lum) * (1.0 - lum);
    // Night darkness has a violet cast (multiplied up it read lavender): the light added is taken from the pixel's colour
    // part-way to grey, then tinted warm, so the lamp's spot reads as a warm white light on the block's own colour.
    vec3 albedo = mix(colour.rgb, vec3(lum), 0.4);
    vec3 lit = colour.rgb + GAIN * light * headroom * albedo * tint;
    fragColor = vec4(min(lit, vec3(1.0)), colour.a);
}
