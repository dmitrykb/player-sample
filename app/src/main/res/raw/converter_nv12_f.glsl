#version 100

precision mediump float;

uniform sampler2D tex;
uniform sampler2D uvTex;
uniform float u_cropRatio;

varying vec2 v_texCoord;

void main(void) {

    float r, g, b, y, u, v;

    vec2 cropCoord = vec2(1., u_cropRatio) * (v_texCoord - vec2(.5)) + vec2(.5);

    y = texture2D(tex, cropCoord).r;
    vec2 uv = texture2D(uvTex, cropCoord).ra;
    u = uv.x;
    v = uv.y;

    y = 1.1643 * (y - 0.0625);
    u = u - 0.5;
    v = v - 0.5;

    r = y + 1.5958 * v;
    g = y - 0.39173 * u - 0.81290 * v;
    b = y + 2.017 * u;

    gl_FragColor = vec4(r, g, b, 1.);
}