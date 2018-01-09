#version 100

precision mediump float;

uniform sampler2D tex;
uniform float u_cropRatio;

varying vec2 v_texCoord;

void main(void) {

    float r, g, b, y, u, v;

    vec2 cropCoord = vec2(1., u_cropRatio) * (v_texCoord - vec2(.5)) + vec2(.5);

    vec4 yuy2 = texture2D(tex, cropCoord);

    if(mod(gl_FragCoord.x, 2.) < 1.)
        y = yuy2.r;
    else
        y = yuy2.b;
    u = yuy2.g;
    v = yuy2.a;

    y = 1.1643 * (y - 0.0625);
    u = u - 0.5;
    v = v - 0.5;

    r = y + 1.5958 * v;
    g = y - 0.39173 * u - 0.81290 * v;
    b = y + 2.017 * u;

    gl_FragColor = vec4(r, g, b, 1.);
}