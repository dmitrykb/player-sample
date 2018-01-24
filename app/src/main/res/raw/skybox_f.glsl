#version 100

precision mediump float;

varying vec2 uvCoord;

uniform sampler2D frame;

void main(void) {
    gl_FragColor = texture2D(frame, uvCoord);
}