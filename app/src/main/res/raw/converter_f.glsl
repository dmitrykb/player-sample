#version 100

precision mediump float;

uniform sampler2D tex;

varying vec2 v_texCoord;

void main(void) {

    float r, g, b, y, u, v;

    vec4 yuy2 = texture2D(tex, v_texCoord);

    if(mod(gl_FragCoord.x, 2.) < .5)
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