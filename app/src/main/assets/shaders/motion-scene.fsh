// Copyright 2026. GPL-3.0-only. Coarse cut guard, evaluated once per source-frame pair.
precision highp float;
uniform sampler2D sampler0;
void main() {
    float mismatches=0.0;
    for(int y=0;y<4;y++) for(int x=0;x<4;x++) {
        float residual=texture2D(sampler0,(vec2(float(x),float(y))+0.5)/4.0).a;
        mismatches+=step(0.12,residual);
    }
    gl_FragColor=vec4(step(12.0,mismatches),0,0,1);
}
