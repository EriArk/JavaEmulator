/* AbyssME two-pass xBRZ resolve, 2026, GPL-3.0-only.
 * Derived from xbrz.glsl (Hyllian / Zenju); retained notices and provenance
 * are in that asset and licenses/RETRO-SHADERS.txt. Keeps upstream blend order,
 * geometry and get_left_ratio, with decisions supplied at guest resolution.
 */
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D Texture;
uniform sampler2D BlendInfo;
uniform mediump vec2 TextureSize;
uniform mediump vec2 OutputSize;
varying vec4 TEX0;

float get_left_ratio(vec2 center, vec2 origin, vec2 direction, vec2 scale)
{
    vec2 P0 = center - origin;
    vec2 proj = direction * (dot(P0, direction) / dot(direction, direction));
    vec2 distv = P0 - proj;
    vec2 orth = vec2(-direction.y, direction.x);
    float side = sign(dot(P0, orth));
    float v = side * length(distv * scale);
    return smoothstep(-sqrt(2.0)/2.0, sqrt(2.0)/2.0, v);
}

void main()
{
    vec2 delta = 1.0 / TextureSize;
    vec2 scale = OutputSize * delta;
    vec2 pos = fract(TEX0.xy * TextureSize) - vec2(0.5);
    vec2 coord = TEX0.xy - pos * delta;
    vec4 info = floor(texture2D(BlendInfo, coord) * 255.0 + 0.5);
    vec3 E = texture2D(Texture, coord).rgb;
    vec3 res = E;
    vec3 B = texture2D(Texture, coord + delta * vec2(0.0,-1.0)).rgb;
    vec3 D = texture2D(Texture, coord + delta * vec2(-1.0,0.0)).rgb;
    vec3 F = texture2D(Texture, coord + delta * vec2(1.0,0.0)).rgb;
    vec3 H = texture2D(Texture, coord + delta * vec2(0.0,1.0)).rgb;
    vec4 lineBlend = mod(floor(info / 2.0), 2.0);
    vec4 shallow = mod(floor(info / 4.0), 2.0);
    vec4 steep = mod(floor(info / 8.0), 2.0);
    vec4 secondColor = mod(floor(info / 16.0), 2.0);

    if (info.z > 0.0) {
        vec2 origin = vec2(0.0,1.0/sqrt(2.0));
        vec2 direction = vec2(1.0,-1.0);
        if (lineBlend.z > 0.0) {
            origin = shallow.z > 0.0 ? vec2(0.0,0.25) : vec2(0.0,0.5);
            direction.x += shallow.z; direction.y -= steep.z;
        }
        res = mix(res,mix(H,F,secondColor.z),get_left_ratio(pos,origin,direction,scale));
    }
    if (info.w > 0.0) {
        vec2 origin = vec2(-1.0/sqrt(2.0),0.0);
        vec2 direction = vec2(1.0,1.0);
        if (lineBlend.w > 0.0) {
            origin = shallow.w > 0.0 ? vec2(-0.25,0.0) : vec2(-0.5,0.0);
            direction.y += shallow.w; direction.x += steep.w;
        }
        res = mix(res,mix(H,D,secondColor.w),get_left_ratio(pos,origin,direction,scale));
    }
    if (info.y > 0.0) {
        vec2 origin = vec2(1.0/sqrt(2.0),0.0);
        vec2 direction = vec2(-1.0,-1.0);
        if (lineBlend.y > 0.0) {
            origin = shallow.y > 0.0 ? vec2(0.25,0.0) : vec2(0.5,0.0);
            direction.y -= shallow.y; direction.x -= steep.y;
        }
        res = mix(res,mix(F,B,secondColor.y),get_left_ratio(pos,origin,direction,scale));
    }
    if (info.x > 0.0) {
        vec2 origin = vec2(0.0,-1.0/sqrt(2.0));
        vec2 direction = vec2(-1.0,1.0);
        if (lineBlend.x > 0.0) {
            origin = shallow.x > 0.0 ? vec2(0.0,-0.25) : vec2(0.0,-0.5);
            direction.x -= shallow.x; direction.y += steep.x;
        }
        res = mix(res,mix(D,B,secondColor.x),get_left_ratio(pos,origin,direction,scale));
    }
    gl_FragColor = vec4(res,1.0);
}
