#version 100

precision mediump float;

uniform sampler2D tex;
uniform sampler2D uvTex;

varying vec2 v_texCoord;

void main(void) {

    float r, g, b, y, u, v;


    y = texture2D(tex, v_texCoord).r;
    vec2 uv = texture2D(uvTex, v_texCoord).ra;
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