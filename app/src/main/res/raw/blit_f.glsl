#version 100

precision mediump float;

varying vec3 pos;

uniform sampler2D frame;

void main(void) {
    gl_FragColor = texture2D(frame, vec2(.5, -.5) * pos.xy + vec2(.5));
}