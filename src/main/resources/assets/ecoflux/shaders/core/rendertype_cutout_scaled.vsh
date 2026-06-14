#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform sampler2D EcofluxScaleTex;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    // Camera-relative block coordinate → matches Java: round(worldBlock - camPos)
    ivec3 camRel = ivec3(round(ChunkOffset + Position));

    // Hash → texture
    int hx = camRel.x * 73856093;
    int hy = camRel.y * 19349663;
    int hz = camRel.z * 83492791;
    int idx = (hx ^ hy ^ hz) & 0x3FFFFF;
    ivec2 uv = ivec2(idx & 2047, (idx >> 11) & 2047);

    vec4 tex = texelFetch(EcofluxScaleTex, uv, 0);
    float scale = tex.r * 2.0;

    // Section-local pivot from GBA channels (encoded: byte = sx*17, norm = byte/255, decode = norm*15)
    vec3 blockCenter = ChunkOffset + vec3(tex.g * 15.0, tex.b * 15.0, tex.a * 15.0) + vec3(0.5);

    // Scale vertex around its block center
    vec3 pos = ChunkOffset + Position;
    pos = mix(blockCenter, pos, scale);

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
