#version 100

precision mediump float;

uniform sampler2D tex;
uniform vec2 u_cropRatio; // (ratio, width)

varying vec2 v_texCoord;

void main(void) {

    float r, g, b, y, u, v;

    float width = u_cropRatio.y - 1.;
    vec2 cropCoord = vec2(1., u_cropRatio.x) * (v_texCoord - vec2(.5)) + vec2(.5);
    vec2 uCoord = vec2(1., .25) * cropCoord + vec2(0., .5);
    uCoord.x = floor(uCoord.x * width / 2.) / width * 2.;

    y = texture2D(tex, vec2(1., .5) * cropCoord).r;
    u = texture2D(tex, uCoord).r;
    uCoord.x += 1. / width;
    v = texture2D(tex, uCoord).r;

    y = 1.1643 * (y - 0.0625);
    u = u - 0.5;
    v = v - 0.5;

    r = y + 1.5958 * v;
    g = y - 0.39173 * u - 0.81290 * v;
    b = y + 2.017 * u;

    gl_FragColor = vec4(r, g, b, 1.);
}