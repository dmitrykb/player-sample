#version 100

precision mediump float;

varying vec2 uvCoord;
varying vec2 preMapUV;
varying float hangle;

uniform sampler2D frame;

void main(void) {
    if(hangle > 1.01 && (preMapUV.x > 1. || preMapUV.x < 0. || preMapUV.y > 1. || preMapUV.y < 0.))
        discard;

    gl_FragColor = texture2D(frame, uvCoord);

}